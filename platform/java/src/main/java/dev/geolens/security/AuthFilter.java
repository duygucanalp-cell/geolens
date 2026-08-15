package dev.geolens.security;

import dev.geolens.auth.AuthException;
import dev.geolens.auth.AuthIdentity;
import dev.geolens.auth.TokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;

/**
 * JWT kimlik doğrulama + rol/tier yetkilendirme filtresi — Go {@code httpmw} portu.
 * <p>Korunan rotalarda Bearer token doğrulanır; tenant, JWT claim'inden çözülür ve
 * {@code X-Tenant-ID} başlığı JWT'deki kiracıyla ezilir (spoofing engeli — Go
 * {@code TenantContext} karşılığı). Rol hiyerarşisi admin &gt; editor &gt; viewer
 * (Go {@code roleWeights}); bilinmeyen/boş rol {@code viewer}'a normalleşir (Go
 * {@code normalizeRole}). Hata gövdeleri Go ile birebir: {@code authorization_required},
 * {@code invalid_token}, {@code insufficient_permissions}, {@code plan_upgrade_required}.
 */
public final class AuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);

    // Rol hiyerarşisi (Go httpmw roleWeights) — admin(3) > editor(2) > viewer(1)
    static final String ROLE_ADMIN = "admin";
    static final String ROLE_EDITOR = "editor";
    static final String ROLE_VIEWER = "viewer";

    // Tier hiyerarşisi (Go httpmw tierWeights)
    static final String TIER_FREE = "free";
    static final String TIER_PRO = "pro";

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final TokenValidator validator;
    private final DSLContext dsl; // tier kontrolü için; yoksa atlanır

    /** (metot, path deseni) — metot "*" tüm metotlar. */
    record Rule(String method, String pattern) {
    }

    // Halka açık rotalar (kimlik doğrulamasız) — Go cmd/api public grupları.
    static final List<String> PUBLIC_PATTERNS = List.of(
            "/v1/auth/register",
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/v1/auth/accept-invitation",
            "/v1/sso/acs/**",
            "/v1/workspaces/*/seo/callback",
            "/public/**", // spike: API key auth ayrı adımda (Go AuthenticateAPIKey)
            "/actuator/**",
            "/health",
            "/error");

    // Yalnızca admin (Go RequireRole(RoleAdmin) rotaları — cmd/api main.go birebir).
    static final List<Rule> ADMIN_RULES = List.of(
            new Rule("*", "/v1/admin/**"),
            new Rule("*", "/v1/compliance/**"),
            new Rule("*", "/v1/sso/**"),
            new Rule("*", "/v1/discovery/**"),
            new Rule("*", "/v1/api-keys/**"),
            new Rule("GET", "/v1/pilot/tenants"),
            new Rule("POST", "/v1/policies/packs/seed"),
            new Rule("POST", "/v1/policies/packs/*/apply"),
            new Rule("PUT", "/v1/policies/controls/**"),
            new Rule("POST", "/v1/workspaces/*/brands"),
            new Rule("PUT", "/v1/workspaces/*/brands/*"),
            new Rule("DELETE", "/v1/workspaces/*/brands/*"),
            new Rule("PUT", "/v1/workspaces/*/brands/*/competitors"),
            new Rule("DELETE", "/v1/workspaces/*/brands/*/competitors/*"),
            // Workspace arşivleme Java'da /archive/ (WebConfig: trailing slash katı)
            new Rule("POST", "/v1/workspaces/*/archive/"),
            new Rule("POST", "/v1/workspaces/*/unarchive"),
            new Rule("POST", "/v1/workspaces/*/transfer"),
            new Rule("DELETE", "/v1/workspaces/*/replay/*"),
            new Rule("DELETE", "/v1/workspaces/*/seo/connections/*"));

    // Viewer'da da izinli yazmalar (Go'da RoleViewer ile açık rotalar).
    static final List<Rule> VIEWER_WRITE_RULES = List.of(
            new Rule("*", "/v1/billing/**"),
            new Rule("*", "/v1/privacy/**"),
            new Rule("*", "/v1/account/**"),
            new Rule("*", "/v1/deletion-requests/**"),
            new Rule("POST", "/v1/auth/logout"),
            new Rule("POST", "/v1/explain/**"),
            new Rule("POST", "/v1/pilot/enroll"),
            new Rule("POST", "/v1/pilot/extend"),
            new Rule("POST", "/v1/pilot/cancel"),
            new Rule("POST", "/v1/agents/traces"),
            new Rule("POST", "/v1/tenant/invitations"));

    // Pro tier gerektiren rotalar (Go RequireTier(TierPro)).
    static final List<Rule> PRO_RULES = List.of(
            new Rule("POST", "/v1/tenant/invitations"),
            new Rule("POST", "/v1/workspaces/*/reports/digest"),
            new Rule("POST", "/v1/workspaces/*/reports/score-card"),
            new Rule("POST", "/v1/workspaces/*/reports/audit"));

    public AuthFilter(TokenValidator validator, DSLContext dsl) {
        this.validator = validator;
        this.dsl = dsl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        // -- Authenticate (Go httpmw.Authenticate) --
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "authorization_required");
            return;
        }
        String tokenStr = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

        AuthIdentity identity;
        try {
            identity = validator.validate(tokenStr);
        } catch (AuthException e) {
            LOG.debug("geçersiz token", e);
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token");
            return;
        }

        String role = normalizeRole(identity.role());

        // -- RequireRole (Go httpmw.RequireRole) --
        String required = requiredRole(request.getMethod(), path);
        if (required != null && !hasSufficientRole(role, required)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "insufficient_permissions");
            return;
        }

        // -- RequireTier (Go httpmw.RequireTier) —
        if (requiresPro(request.getMethod(), path) && !hasSufficientTier(tenantTier(identity.tenantId()), TIER_PRO)) {
            writeError(response, 402, "plan_upgrade_required");
            return;
        }

        request.setAttribute("geolens.tenantId", identity.tenantId());
        request.setAttribute("geolens.userId", identity.userId());
        request.setAttribute("geolens.role", role);

        // Tenant başlığını JWT'den gelen kiracıyla ez (spoofing engeli)
        chain.doFilter(new TenantRequestWrapper(request, identity.tenantId()), response);
    }

    private boolean isPublic(String path) {
        for (String pattern : PUBLIC_PATTERNS) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /** Rota için gereken minimum rol; null = herhangi bir kimlikli kullanıcı (GET varsayılanı). */
    private String requiredRole(String method, String path) {
        for (Rule r : ADMIN_RULES) {
            if (methodMatches(r, method) && matcher.match(r.pattern(), path)) {
                return ROLE_ADMIN;
            }
        }
        for (Rule r : VIEWER_WRITE_RULES) {
            if (methodMatches(r, method) && matcher.match(r.pattern(), path)) {
                return ROLE_VIEWER;
            }
        }
        if (isWriteMethod(method)) {
            return ROLE_EDITOR;
        }
        return null;
    }

    private boolean requiresPro(String method, String path) {
        for (Rule r : PRO_RULES) {
            if (methodMatches(r, method) && matcher.match(r.pattern(), path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean methodMatches(Rule r, String method) {
        return "*".equals(r.method()) || r.method().equalsIgnoreCase(method);
    }

    private static boolean isWriteMethod(String method) {
        return method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT")
                || method.equalsIgnoreCase("DELETE") || method.equalsIgnoreCase("PATCH");
    }

    /** Bilinmeyen/boş rol → viewer (Go normalizeRole). */
    static String normalizeRole(String role) {
        return switch (role) {
            case ROLE_ADMIN, ROLE_EDITOR, ROLE_VIEWER -> role;
            default -> ROLE_VIEWER;
        };
    }

    /** Rol hiyerarşisi kontrolü — Go hasSufficientRole. */
    static boolean hasSufficientRole(String userRole, String minimumRole) {
        return roleWeight(userRole) >= roleWeight(minimumRole) && roleWeight(minimumRole) > 0;
    }

    private static int roleWeight(String role) {
        return switch (role) {
            case ROLE_ADMIN -> 3;
            case ROLE_EDITOR -> 2;
            case ROLE_VIEWER -> 1;
            default -> 0;
        };
    }

    private static boolean hasSufficientTier(String current, String minimum) {
        return tierWeight(current) >= tierWeight(minimum);
    }

    private static int tierWeight(String tier) {
        return switch (tier) {
            case "enterprise" -> 3;
            case "business" -> 2;
            case TIER_PRO -> 1;
            default -> 0; // free / bilinmeyen
        };
    }

    /** Kiracının tier'ını DB'den okur — Go RequireTier sorgusu; hata/eksikte free. */
    private String tenantTier(String tenantId) {
        if (dsl == null || tenantId == null || tenantId.isBlank()) {
            return TIER_FREE;
        }
        try {
            var rec = dsl.fetchOne("SELECT tier FROM identity.tenants WHERE id = ?", tenantId);
            return rec == null ? TIER_FREE : String.valueOf(rec.get(0));
        } catch (RuntimeException e) {
            LOG.warn("tier sorgusu başarısız, free sayılıyor — tenant {}", tenantId);
            return TIER_FREE;
        }
    }

    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String message = switch (code) {
            case "authorization_required" -> "kimlik doğrulama gerekli";
            case "invalid_token" -> "geçersiz token";
            case "insufficient_permissions" -> "yetki yetersiz";
            case "plan_upgrade_required" -> "Bu özellik için paket yükseltmesi gerekli";
            default -> code;
        };
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    /** X-Tenant-ID başlığını JWT'deki kiracıyla ezer — Go TenantContext karşılığı. */
    static final class TenantRequestWrapper extends HttpServletRequestWrapper {

        private final String tenantId;

        TenantRequestWrapper(HttpServletRequest request, String tenantId) {
            super(request);
            this.tenantId = tenantId;
        }

        @Override
        public String getHeader(String name) {
            return "X-Tenant-ID".equalsIgnoreCase(name) ? tenantId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return "X-Tenant-ID".equalsIgnoreCase(name)
                    ? java.util.Collections.enumeration(List.of(tenantId))
                    : super.getHeaders(name);
        }
    }
}
