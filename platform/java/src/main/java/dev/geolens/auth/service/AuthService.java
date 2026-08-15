package dev.geolens.auth.service;

import dev.geolens.common.ServiceException;

import dev.geolens.auth.AuthException;
import dev.geolens.auth.Claims;
import dev.geolens.auth.JWTService;
import dev.geolens.auth.TokenBlacklist;
import dev.geolens.auth.TokenResult;
import dev.geolens.auth.web.AcceptInvitationRequest;
import dev.geolens.auth.web.AuthResponse;
import dev.geolens.auth.web.InviteRequest;
import dev.geolens.auth.web.LoginRequest;
import dev.geolens.auth.web.RegisterRequest;
import dev.geolens.auth.web.TransactionalMailer;
import dev.geolens.auth.web.UpdateRoleRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kimlik doğrulama ve yetkilendirme iş mantığı — Go {@code auth.handler} portu.
 * <p>Kayıt, giriş, yenileme, çıkış, davet ve üyelik yönetimini yapar. Controller
 * yalnızca HTTP katmanıdır; bu sınıf bcrypt, JWT, transaction ve DB erişimini içerir.
 */
@Service
public class AuthService {

    private static final Set<String> VALID_ROLES = Set.of("admin", "editor", "viewer");
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final JWTService jwt;
    private final DSLContext dsl;
    private final TransactionTemplate tx;
    private final TokenBlacklist blacklist;
    private final TransactionalMailer mail;
    private final String baseUrl;

    public AuthService(JWTService jwt, DSLContext dsl, TransactionTemplate tx,
                       TokenBlacklist blacklist, TransactionalMailer mail,
                       @Value("${app.base-url:}") String baseUrl) {
        this.jwt = jwt;
        this.dsl = dsl;
        this.tx = tx;
        this.blacklist = blacklist;
        this.mail = mail;
        this.baseUrl = baseUrl;
    }

    public AuthResponse register(RegisterRequest req) {
        String hashed = ENCODER.encode(req.password());

        String[] ids = txExecute(() -> {
            // Go handler parity: tenant slug çakışması 409, e-posta çakışması 409 döner
            String tenantId;
            try {
                tenantId = value("""
                        INSERT INTO identity.tenants (id, name, slug, tier)
                        VALUES (gen_random_uuid()::text, ?, lower(regexp_replace(?, '[^a-z0-9]', '', 'g')), 'free')
                        RETURNING id
                        """, String.class, req.name(), req.name());
            } catch (RuntimeException e) {
                throw new ServiceException(HttpStatus.CONFLICT, "bu isimle kayıt yapılamaz");
            }
            String userId;
            try {
                userId = value("""
                        INSERT INTO identity.users (id, tenant_id, email, password_hash, role, full_name)
                        VALUES (gen_random_uuid()::text, ?, ?, ?, 'admin', ?)
                        RETURNING id
                        """, String.class, tenantId, req.email(), hashed, req.name());
            } catch (RuntimeException e) {
                throw new ServiceException(HttpStatus.CONFLICT, "bu e-posta zaten kayıtlı");
            }
            String workspaceId = value("""
                    INSERT INTO config.workspaces (id, tenant_id, name, slug)
                    VALUES (gen_random_uuid()::text, ?, 'Varsayılan Çalışma Alanı', 'default')
                    RETURNING id
                    """, String.class, tenantId);
            dsl.execute("""
                    INSERT INTO config.memberships (id, workspace_id, user_id, tenant_id, role)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, 'admin')
                    """, workspaceId, userId, tenantId);
            return new String[]{userId, tenantId, workspaceId};
        });

        TokenResult token = jwt.generateToken(ids[0], ids[1], "admin");
        return new AuthResponse(token.token(), format(token.expiresAt()), ids[0], ids[1], ids[2], "admin");
    }

    public AuthResponse login(LoginRequest req) {
        Map<String, Object> user;
        try {
            user = map("""
                    SELECT u.id, u.tenant_id, u.password_hash, u.role
                    FROM identity.users u
                    WHERE u.email = ? AND u.is_active = true
                    """, req.email());
            if (user == null) {
                throw new ServiceException(HttpStatus.UNAUTHORIZED, "geçersiz e-posta veya şifre");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "giriş başarısız");
        }

        String userId = String.valueOf(user.get("id"));
        String tenantId = String.valueOf(user.get("tenant_id"));
        String passwordHash = String.valueOf(user.get("password_hash"));
        String role = String.valueOf(user.get("role"));

        if (!ENCODER.matches(req.password(), passwordHash)) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "geçersiz e-posta veya şifre");
        }

        String workspaceId = firstWorkspace(userId, tenantId);
        String rbacRole = resolveRBACRole(userId, tenantId, role);
        TokenResult token = jwt.generateToken(userId, tenantId, rbacRole);
        return new AuthResponse(token.token(), format(token.expiresAt()),
                userId, tenantId, workspaceId, rbacRole);
    }

    public AuthResponse refresh(String tokenStr) {
        Claims claims;
        try {
            claims = jwt.validateToken(tokenStr);
        } catch (AuthException e) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "oturum süresi dolmuş veya geçersiz token");
        }

        if (blacklist != null && claims.id() != null && !claims.id().isBlank()
                && blacklist.exists("token:blacklist:" + claims.id())) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "token iptal edilmiş");
        }

        // Maksimum oturum ömrü: 7 günden eski token'lar yenilenemez (kayan oturum).
        if (claims.issuedAt() != null && Instant.now().isAfter(claims.issuedAt().plusSeconds(7 * 24 * 3600))) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "oturum süresi sona erdi, tekrar giriş yapın");
        }

        // Rolü DB'den taze oku; kullanıcı deaktif/silinmişse yenileme reddedilir.
        String role;
        try {
            role = value("""
                    SELECT role FROM identity.users WHERE id = ? AND tenant_id = ? AND is_active = true
                    """, String.class, claims.userId(), claims.tenantId());
            if (role == null) {
                throw new ServiceException(HttpStatus.UNAUTHORIZED, "oturum sonlandırıldı, tekrar giriş yapın");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "oturum sonlandırıldı, tekrar giriş yapın");
        }

        String rbacRole = resolveRBACRole(claims.userId(), claims.tenantId(), role);
        TokenResult token = jwt.generateToken(claims.userId(), claims.tenantId(), rbacRole);
        return new AuthResponse(token.token(), format(token.expiresAt()),
                claims.userId(), claims.tenantId(), "", rbacRole);
    }

    public void logout(String tokenStr) {
        if (!tokenStr.isEmpty()) {
            try {
                jwt.blacklistToken(tokenStr, blacklist);
            } catch (AuthException ignored) {
                // blacklist ekleme hatası non-fatal (Go warn + devam)
            }
        }
    }

    public Map<String, Object> getTenant(String tenantId) {
        Map<String, Object> tenant;
        try {
            tenant = map("""
                    SELECT name, slug, tier, created_at FROM identity.tenants WHERE id = ?
                    """, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "kiracı bulunamadı");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", tenantId);
        body.put("name", tenant.get("name"));
        body.put("slug", tenant.get("slug"));
        body.put("tier", tenant.get("tier"));
        body.put("created_at", tenant.get("created_at") == null ? null : String.valueOf(tenant.get("created_at")));
        return body;
    }

    public Map<String, Object> listMembers(String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT u.id, u.email, u.full_name, m.role AS workspace_role, m.workspace_id, u.created_at
                    FROM identity.users u
                    JOIN config.memberships m ON m.user_id = u.id AND m.tenant_id = u.tenant_id
                    WHERE u.tenant_id = ? AND u.is_active = true
                    ORDER BY u.created_at DESC
                    """, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "üyeler listelenemedi");
        }
        List<Map<String, Object>> members = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("user_id", r.get("id"));
            m.put("email", r.get("email"));
            m.put("full_name", r.get("full_name"));
            m.put("workspace_role", r.get("workspace_role"));
            m.put("workspace_id", r.get("workspace_id"));
            m.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            members.add(m);
        }
        return Map.of("members", members);
    }

    public Map<String, Object> updateMemberRole(String tenantId, String userId, UpdateRoleRequest req) {
        int updated;
        try {
            updated = dsl.execute("""
                    UPDATE config.memberships
                    SET role = ?
                    WHERE user_id = ? AND tenant_id = ?
                    """, req.role(), userId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "rol güncellenemedi");
        }
        if (updated == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "kullanıcı bulunamadı");
        }
        return Map.of("status", "updated", "role", req.role());
    }

    public Map<String, Object> inviteMember(String tenantId, String userId, InviteRequest req) {
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);

        try {
            dsl.execute("""
                    INSERT INTO identity.invitations (id, tenant_id, workspace_id, invited_by, email, role, token, expires_at)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, now() + interval '7 days')
                    """, tenantId, req.workspaceId(), userId == null ? "" : userId,
                    req.email(), req.role(), token);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.CONFLICT, "bu e-posta zaten davet edilmiş");
        }

        boolean emailSent = false;
        if (mail != null && baseUrl != null && !baseUrl.isBlank()) {
            String acceptUrl = String.format("%s/#/invite?token=%s&email=%s",
                    baseUrl.replaceAll("/+$", ""),
                    urlEncode(token), urlEncode(req.email()));
            String subject = "GeoLens Daveti — çalışma alanına katılın";
            String body = "<h2>GeoLens'e davet edildiniz</h2>"
                    + "<p><strong>" + escapeHtml(req.email()) + "</strong> e-posta adresi, bir GeoLens çalışma alanına davet edildi.</p>"
                    + "<p>Daveti kabul etmek ve hesabınızı oluşturmak için aşağıdaki bağlantıyı kullanın:</p>"
                    + "<p><a href=\"" + acceptUrl + "\">Daveti Kabul Et</a></p>"
                    + "<p>Bağlantı <strong>7 gün</strong> geçerlidir. Bağlantı sorunluysa davet token'ınız:</p>"
                    + "<p><code>" + escapeHtml(token) + "</code></p>";
            try {
                mail.sendEmail(req.email(), subject, body);
                emailSent = true;
            } catch (RuntimeException ignored) {
                // gönderim hatası non-fatal (Go warn + devam)
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "invited");
        body.put("email", req.email());
        body.put("token", token);
        body.put("email_sent", emailSent);
        return body;
    }

    public AuthResponse acceptInvitation(AcceptInvitationRequest req) {
        Map<String, Object> invitation;
        try {
            invitation = map("""
                    SELECT id, tenant_id, workspace_id, role, expires_at
                    FROM identity.invitations
                    WHERE token = ? AND accepted_at IS NULL
                    """, req.token());
            if (invitation == null) {
                throw new ServiceException(HttpStatus.NOT_FOUND, "geçersiz veya süresi dolmuş davet");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "geçersiz veya süresi dolmuş davet");
        }

        String invitationId = String.valueOf(invitation.get("id"));
        String tenantId = String.valueOf(invitation.get("tenant_id"));
        String workspaceId = String.valueOf(invitation.get("workspace_id"));
        String role = String.valueOf(invitation.get("role"));

        Instant expiresAt = invitation.get("expires_at") instanceof java.sql.Timestamp ts
                ? ts.toInstant() : null;
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            throw new ServiceException(HttpStatus.GONE, "davetin süresi dolmuş");
        }

        String userId = findOrCreateUser(req.email(), req.password(), req.name(), tenantId);
        if (userId == null) {
            throw new ServiceException(HttpStatus.CONFLICT, "bu e-posta zaten kayıtlı");
        }

        try {
            dsl.execute("""
                    INSERT INTO config.memberships (id, workspace_id, user_id, tenant_id, role)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?)
                    ON CONFLICT (workspace_id, user_id) DO UPDATE SET role = ?
                    """, workspaceId, userId, tenantId, role, role);
            dsl.execute("UPDATE identity.invitations SET accepted_at = now() WHERE id = ?", invitationId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "davet kabul edilemedi");
        }

        String rbacRole = resolveRBACRole(userId, tenantId, role);
        TokenResult token = jwt.generateToken(userId, tenantId, rbacRole);
        return new AuthResponse(token.token(), format(token.expiresAt()),
                userId, tenantId, workspaceId, rbacRole);
    }

    public Map<String, Object> listInvitations(String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, email, role, workspace_id, created_at, expires_at, accepted_at IS NOT NULL AS accepted
                    FROM identity.invitations
                    WHERE tenant_id = ?
                    ORDER BY created_at DESC
                    LIMIT 50
                    """, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "davetler listelenemedi");
        }
        List<Map<String, Object>> invitations = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> inv = new LinkedHashMap<>();
            inv.put("id", r.get("id"));
            inv.put("email", r.get("email"));
            inv.put("role", r.get("role"));
            inv.put("workspace_id", r.get("workspace_id"));
            inv.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            inv.put("expires_at", r.get("expires_at") == null ? null : String.valueOf(r.get("expires_at")));
            inv.put("accepted", r.get("accepted"));
            invitations.add(inv);
        }
        return Map.of("invitations", invitations);
    }

    /**
     * Kullanıcının gerçek RBAC rolünü config.memberships üzerinden çözer — Go
     * {@code resolveRBACRole} portu. identity.users.role 'member' olabilir (RBAC
     * sözlüğüyle uyumsuz); birden çok üyelikte EN YÜKSEK yetki seçilir. Üyelik yoksa
     * fallback normalizasyonu: bilinmeyen/değerler 'viewer'a eşlenir.
     */
    public String resolveRBACRole(String userId, String tenantId, String fallbackRole) {
        String role;
        try {
            role = value("""
                    SELECT m.role FROM config.memberships m
                    WHERE m.user_id = ? AND m.tenant_id = ?
                    ORDER BY CASE m.role WHEN 'admin' THEN 3 WHEN 'editor' THEN 2 ELSE 1 END DESC, m.created_at
                    LIMIT 1
                    """, String.class, userId, tenantId);
        } catch (RuntimeException e) {
            role = fallbackRole;
        }
        if (role == null || role.isBlank()) {
            role = fallbackRole;
        }
        switch (role) {
            case "admin":
            case "editor":
            case "viewer":
                return role;
            default:
                return "viewer";
        }
    }

    private String firstWorkspace(String userId, String tenantId) {
        try {
            String w = value("""
                    SELECT m.workspace_id FROM config.memberships m
                    WHERE m.user_id = ? AND m.tenant_id = ?
                    ORDER BY m.created_at LIMIT 1
                    """, String.class, userId, tenantId);
            return w == null ? "" : w;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String findOrCreateUser(String email, String password, String name, String tenantId) {
        String existing;
        try {
            existing = value("""
                    SELECT id FROM identity.users WHERE email = ? AND tenant_id = ?
                    """, String.class, email, tenantId);
        } catch (RuntimeException e) {
            return null;
        }
        if (existing != null) {
            return existing;
        }
        String hashed = ENCODER.encode(password);
        try {
            return value("""
                    INSERT INTO identity.users (id, tenant_id, email, password_hash, role, full_name)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, 'member', ?)
                    RETURNING id
                    """, String.class, tenantId, email, hashed, name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String[] txExecute(java.util.function.Supplier<String[]> action) {
        if (tx == null) {
            throw new AuthException("transaction mevcut değil");
        }
        try {
            return tx.execute(status -> action.get());
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String format(Instant instant) {
        return ISO.format(instant);
    }

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    /** ADR-014: plain SQL tek değer — jOOQ dönüşümüyle (fetchValue raw Object döner). */
    private <T> T value(String sql, Class<T> type, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.get(0, type);
    }
}
