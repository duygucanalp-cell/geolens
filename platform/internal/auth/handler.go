package auth

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5"
	"github.com/redis/go-redis/v9"
	"golang.org/x/crypto/bcrypt"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// ---- Request/Response Types ----

type registerRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
	Name     string `json:"name"`
}

type loginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type authResponse struct {
	Token       string `json:"token"`
	ExpiresAt   string `json:"expires_at"`
	UserID      string `json:"user_id"`
	TenantID    string `json:"tenant_id"`
	WorkspaceID string `json:"workspace_id"`
	Role        string `json:"role"`
}

type updateRoleRequest struct {
	Role string `json:"role"`
}

type memberResponse struct {
	UserID        string `json:"user_id"`
	Email         string `json:"email"`
	FullName      string `json:"full_name"`
	WorkspaceRole string `json:"workspace_role"`
	WorkspaceID   string `json:"workspace_id"`
	CreatedAt     string `json:"created_at"`
}

// Handler holds dependencies for auth HTTP handlers.
type Handler struct {
	pool    dbiface.DB
	rawPool *db.Pool
	jwt     *JWTService
	rdb     *redis.Client
}

// NewHandler creates a new auth handler.
func NewHandler(pool *db.Pool, jwt *JWTService, rdb *redis.Client) *Handler {
	return &Handler{
		pool:    dbiface.NewAdapter(pool),
		rawPool: pool,
		jwt:     jwt,
		rdb:     rdb,
	}
}

// Register handles POST /v1/auth/register
// Self-serve kayıt: e-posta + şifre + isim, ödeme bilgisi istenmez (D-07).
// Yeni kiracı + yönetici kullanıcı oluşturur.
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}

	if req.Email == "" || req.Password == "" || req.Name == "" {
		httputil.WriteError(w, http.StatusBadRequest, "e-posta, şifre ve isim zorunludur")
		return
	}

	if len(req.Password) < 8 {
		httputil.WriteError(w, http.StatusBadRequest, "şifre en az 8 karakter olmalıdır")
		return
	}

	// Hash password
	hashedPW, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		slog.Error("şifre hash hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}

	// Transaction: create tenant + user
	ctx := r.Context()
	tx, err := h.pool.Begin(ctx)
	if err != nil {
		slog.Error("transaction başlatma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}
	defer tx.Rollback(ctx)

	var tenantID, userID, workspaceID string
	err = tx.QueryRow(ctx, `
		INSERT INTO identity.tenants (id, name, slug, tier)
		VALUES (gen_random_uuid()::text, $1, lower(regexp_replace($1, '[^a-z0-9]', '', 'g')), 'free')
		RETURNING id
	`, req.Name).Scan(&tenantID)
	if err != nil {
		slog.Error("kiracı oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusConflict, "bu isimle kayıt yapılamaz")
		return
	}

	err = tx.QueryRow(ctx, `
		INSERT INTO identity.users (id, tenant_id, email, password_hash, role, full_name)
		VALUES (gen_random_uuid()::text, $1, $2, $3, 'admin', $4)
		RETURNING id
	`, tenantID, req.Email, string(hashedPW), req.Name).Scan(&userID)
	if err != nil {
		slog.Error("kullanıcı oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusConflict, "bu e-posta zaten kayıtlı")
		return
	}

	// Varsayılan çalışma alanı oluştur
	err = tx.QueryRow(ctx, `
		INSERT INTO config.workspaces (id, tenant_id, name, slug)
		VALUES (gen_random_uuid()::text, $1, 'Varsayılan Çalışma Alanı', 'default')
		RETURNING id
	`, tenantID).Scan(&workspaceID)
	if err != nil {
		slog.Error("çalışma alanı oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}

	// Kullanıcıyı çalışma alanına admin olarak ekle
	_, err = tx.Exec(ctx, `
		INSERT INTO config.memberships (id, workspace_id, user_id, tenant_id, role)
		VALUES (gen_random_uuid()::text, $1, $2, $3, 'admin')
	`, workspaceID, userID, tenantID)
	if err != nil {
		slog.Error("üyelik oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}

	if err := tx.Commit(ctx); err != nil {
		slog.Error("transaction commit hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}

	// Generate JWT
	token, expiresAt, err := h.jwt.GenerateToken(userID, tenantID, "admin")
	if err != nil {
		slog.Error("jwt oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, authResponse{
		Token:       token,
		ExpiresAt:   expiresAt.Format(time.RFC3339),
		UserID:      userID,
		TenantID:    tenantID,
		WorkspaceID: workspaceID,
		Role:        "admin",
	})
}

// Login handles POST /v1/auth/login
func (h *Handler) Login(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}

	if req.Email == "" || req.Password == "" {
		httputil.WriteError(w, http.StatusBadRequest, "e-posta ve şifre zorunludur")
		return
	}

	ctx := r.Context()
	var userID, tenantID, passwordHash, role, workspaceID string
	err := h.pool.QueryRow(ctx, `
		SELECT u.id, u.tenant_id, u.password_hash, u.role
		FROM identity.users u
		WHERE u.email = $1 AND u.is_active = true
	`, req.Email).Scan(&userID, &tenantID, &passwordHash, &role)

	if errors.Is(err, pgx.ErrNoRows) {
		httputil.WriteError(w, http.StatusUnauthorized, "geçersiz e-posta veya şifre")
		return
	}
	if err != nil {
		slog.Error("kullanıcı sorgu hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "giriş başarısız")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(passwordHash), []byte(req.Password)); err != nil {
		httputil.WriteError(w, http.StatusUnauthorized, "geçersiz e-posta veya şifre")
		return
	}

	// Kullanıcının ilk çalışma alanını bul
	err = h.pool.QueryRow(ctx, `
		SELECT m.workspace_id FROM config.memberships m
		WHERE m.user_id = $1 AND m.tenant_id = $2
		ORDER BY m.created_at LIMIT 1
	`, userID, tenantID).Scan(&workspaceID)
	if err != nil {
		workspaceID = ""
	}

	token, expiresAt, err := h.jwt.GenerateToken(userID, tenantID, role)
	if err != nil {
		slog.Error("jwt oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "giriş başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, authResponse{
		Token:       token,
		ExpiresAt:   expiresAt.Format(time.RFC3339),
		UserID:      userID,
		TenantID:    tenantID,
		WorkspaceID: workspaceID,
		Role:        role,
	})
}

// Logout handles POST /v1/auth/logout
// Token'ı Redis blacklist'e ekler, böylece logout sonrası kullanılamaz.
func (h *Handler) Logout(w http.ResponseWriter, r *http.Request) {
	authHeader := r.Header.Get("Authorization")
	if authHeader != "" && strings.HasPrefix(authHeader, "Bearer ") {
		tokenStr := strings.TrimPrefix(authHeader, "Bearer ")
		if tokenStr != "" {
			if err := h.jwt.BlacklistToken(tokenStr, h.rdb); err != nil {
				slog.Warn("token blacklist ekleme hatası", "error", err)
			}
		}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "logged_out"})
}

// ListMembers handles GET /v1/tenant/members
// Kiracıdaki tüm kullanıcıları ve üyeliklerini listeler.
func (h *Handler) ListMembers(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT u.id, u.email, u.full_name, m.role, m.workspace_id, u.created_at
		FROM identity.users u
		JOIN config.memberships m ON m.user_id = u.id AND m.tenant_id = u.tenant_id
		WHERE u.tenant_id = $1 AND u.is_active = true
		ORDER BY u.created_at DESC
	`, tenantID)
	if err != nil {
		slog.Error("üye listeleme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "üyeler listelenemedi")
		return
	}
	defer rows.Close()

	var members []memberResponse
	for rows.Next() {
		var m memberResponse
		if err := rows.Scan(&m.UserID, &m.Email, &m.FullName, &m.WorkspaceRole, &m.WorkspaceID, &m.CreatedAt); err != nil {
			slog.Warn("üye satır okuma hatası", "error", err)
			continue
		}
		members = append(members, m)
	}
	if err := rows.Err(); err != nil {
		slog.Error("üye satır okuma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "üyeler listelenemedi")
		return
	}

	if members == nil {
		members = []memberResponse{}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"members": members})
}

// UpdateMemberRole handles PATCH /v1/tenant/members/{userId}/role
// Bir kullanıcının workspace üyelik rolünü günceller.
func (h *Handler) UpdateMemberRole(w http.ResponseWriter, r *http.Request) {
	userID := chi.URLParam(r, "userId")
	if userID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "kullanıcı ID gerekli")
		return
	}

	var req updateRoleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}

	validRoles := map[string]bool{"admin": true, "editor": true, "viewer": true}
	if !validRoles[req.Role] {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz rol (admin, editor, viewer)")
		return
	}

	tenantID := httpmw.GetTenantID(r.Context())

	result, err := h.pool.Exec(r.Context(), `
		UPDATE config.memberships
		SET role = $1
		WHERE user_id = $2 AND tenant_id = $3
	`, req.Role, userID, tenantID)
	if err != nil {
		slog.Error("rol güncelleme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "rol güncellenemedi")
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteError(w, http.StatusNotFound, "kullanıcı bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "updated", "role": req.Role})
}

// InviteMember handles POST /v1/tenant/invitations
// Bir e-posta adresine davet gönderir (admin yetkisi gerekir).
func (h *Handler) InviteMember(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	userID := httpmw.GetUserID(r.Context())

	var req struct {
		Email       string `json:"email"`
		WorkspaceID string `json:"workspace_id"`
		Role        string `json:"role"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.Email == "" || req.WorkspaceID == "" || req.Role == "" {
		httputil.WriteError(w, http.StatusBadRequest, "e-posta, çalışma alanı ve rol zorunludur")
		return
	}

	validRoles := map[string]bool{"admin": true, "editor": true, "viewer": true}
	if !validRoles[req.Role] {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz rol (admin, editor, viewer)")
		return
	}

	// Davet token'ı oluştur
	tokenBytes := make([]byte, 32)
	if _, err := rand.Read(tokenBytes); err != nil {
		slog.Error("davet token oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "davet oluşturulamadı")
		return
	}
	token := hex.EncodeToString(tokenBytes)

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO identity.invitations (id, tenant_id, workspace_id, invited_by, email, role, token, expires_at)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4, $5, $6, now() + interval '7 days')
	`, tenantID, req.WorkspaceID, userID, req.Email, req.Role, token)
	if err != nil {
		slog.Error("davet kaydetme hatası", "error", err)
		httputil.WriteError(w, http.StatusConflict, "bu e-posta zaten davet edilmiş")
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, map[string]string{
		"status": "invited",
		"email":  req.Email,
		"token":  token,
	})
}

// AcceptInvitation handles POST /v1/auth/accept-invitation
// Davet token'ı ile üyeliği kabul eder (token + kayıt bilgileri).
func (h *Handler) AcceptInvitation(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Token    string `json:"token"`
		Email    string `json:"email"`
		Password string `json:"password"`
		Name     string `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.Token == "" || req.Email == "" || req.Password == "" || req.Name == "" {
		httputil.WriteError(w, http.StatusBadRequest, "token, e-posta, şifre ve isim zorunludur")
		return
	}

	// Daveti bul
	var invitationID, tenantID, workspaceID, role string
	var expiresAt time.Time
	err := h.pool.QueryRow(r.Context(), `
		SELECT id, tenant_id, workspace_id, role, expires_at
		FROM identity.invitations
		WHERE token = $1 AND accepted_at IS NULL
	`, req.Token).Scan(&invitationID, &tenantID, &workspaceID, &role, &expiresAt)
	if err != nil {
		httputil.WriteError(w, http.StatusNotFound, "geçersiz veya süresi dolmuş davet")
		return
	}

	if time.Now().After(expiresAt) {
		httputil.WriteError(w, http.StatusGone, "davetin süresi dolmuş")
		return
	}

	// Kullanıcı var mı kontrol et
	var existingUserID string
	err = h.pool.QueryRow(r.Context(), `
		SELECT id FROM identity.users WHERE email = $1 AND tenant_id = $2
	`, req.Email, tenantID).Scan(&existingUserID)
	if err != nil && err != pgx.ErrNoRows {
		slog.Error("kullanıcı sorgu hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "davet kabul edilemedi")
		return
	}

	var userID string
	if existingUserID != "" {
		userID = existingUserID
	} else {
		// Yeni kullanıcı oluştur
		hashedPW, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
		if err != nil {
			slog.Error("şifre hash hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "davet kabul edilemedi")
			return
		}
		err = h.pool.QueryRow(r.Context(), `
			INSERT INTO identity.users (id, tenant_id, email, password_hash, role, full_name)
			VALUES (gen_random_uuid()::text, $1, $2, $3, 'member', $4)
			RETURNING id
		`, tenantID, req.Email, string(hashedPW), req.Name).Scan(&userID)
		if err != nil {
			slog.Error("kullanıcı oluşturma hatası", "error", err)
			httputil.WriteError(w, http.StatusConflict, "bu e-posta zaten kayıtlı")
			return
		}
	}

	// Üyelik oluştur
	_, err = h.pool.Exec(r.Context(), `
		INSERT INTO config.memberships (id, workspace_id, user_id, tenant_id, role)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4)
		ON CONFLICT (workspace_id, user_id) DO UPDATE SET role = $4
	`, workspaceID, userID, tenantID, role)
	if err != nil {
		slog.Error("üyelik oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "davet kabul edilemedi")
		return
	}

	// Daveti işaretle
	if _, err := h.pool.Exec(r.Context(), `
		UPDATE identity.invitations SET accepted_at = now() WHERE id = $1
	`, invitationID); err != nil {
		slog.Warn("davet işaretleme hatası (non-fatal)", "invitation_id", invitationID, "error", err)
	}

	// JWT üret
	token, expiresAtJWT, err := h.jwt.GenerateToken(userID, tenantID, role)
	if err != nil {
		slog.Error("jwt oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "davet kabul edildi ama giriş yapılamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, authResponse{
		Token:       token,
		ExpiresAt:   expiresAtJWT.Format(time.RFC3339),
		UserID:      userID,
		TenantID:    tenantID,
		WorkspaceID: workspaceID,
		Role:        role,
	})
}

// ListInvitations handles GET /v1/tenant/invitations
// Kiracıdaki bekleyen davetleri listeler.
func (h *Handler) ListInvitations(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, email, role, workspace_id, created_at, expires_at, accepted_at IS NOT NULL
		FROM identity.invitations
		WHERE tenant_id = $1
		ORDER BY created_at DESC
		LIMIT 50
	`, tenantID)
	if err != nil {
		slog.Error("davet listeleme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "davetler listelenemedi")
		return
	}
	defer rows.Close()

	type invitationRow struct {
		ID          string `json:"id"`
		Email       string `json:"email"`
		Role        string `json:"role"`
		WorkspaceID string `json:"workspace_id"`
		CreatedAt   string `json:"created_at"`
		ExpiresAt   string `json:"expires_at"`
		Accepted    bool   `json:"accepted"`
	}

	invitations := make([]invitationRow, 0)
	for rows.Next() {
		var inv invitationRow
		var createdAt, expiresAt time.Time
		var accepted bool
		if err := rows.Scan(&inv.ID, &inv.Email, &inv.Role, &inv.WorkspaceID, &createdAt, &expiresAt, &accepted); err != nil {
			slog.Warn("davet satır okuma hatası", "error", err)
			continue
		}
		inv.CreatedAt = createdAt.Format(time.RFC3339)
		inv.ExpiresAt = expiresAt.Format(time.RFC3339)
		inv.Accepted = accepted
		invitations = append(invitations, inv)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"invitations": invitations})
}

// GetTenant handles GET /v1/tenant
// Oturum açmış kullanıcının kiracı bilgilerini döndürür.
func (h *Handler) GetTenant(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	if tenantID == "" {
		httputil.WriteError(w, http.StatusUnauthorized, "kimlik doğrulama gerekli")
		return
	}

	var name, slug, tier string
	var createdAt time.Time
	err := h.pool.QueryRow(r.Context(), `
		SELECT name, slug, tier, created_at FROM identity.tenants WHERE id = $1
	`, tenantID).Scan(&name, &slug, &tier, &createdAt)
	if err != nil {
		slog.Error("kiracı sorgu hatası", "error", err)
		httputil.WriteError(w, http.StatusNotFound, "kiracı bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"id":         tenantID,
		"name":       name,
		"slug":       slug,
		"tier":       tier,
		"created_at": createdAt.Format(time.RFC3339),
	})
}
