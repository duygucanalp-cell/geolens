package httpmw

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/go-chi/chi/v5"
)

// =============================================================================
// Temel middleware testleri
// =============================================================================

func TestPanicRecovery(t *testing.T) {
	handler := PanicRecovery(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		panic("test panic")
	}))

	req := httptest.NewRequest("GET", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("beklenen 500, gerçek %d", w.Code)
	}
}

func TestRequestID(t *testing.T) {
	handler := RequestID(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := GetRequestID(r.Context())
		if id == "" {
			t.Error("RequestID context'te bulunamadı")
		}
	}))

	req := httptest.NewRequest("GET", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Header().Get("X-Request-ID") == "" {
		t.Error("X-Request-ID header'ı eksik")
	}
}

func TestRequestID_FromHeader(t *testing.T) {
	var capturedID string
	handler := RequestID(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedID = GetRequestID(r.Context())
	}))

	req := httptest.NewRequest("GET", "/test", nil)
	req.Header.Set("X-Request-ID", "client-provided-id")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if capturedID != "client-provided-id" {
		t.Errorf("beklenen 'client-provided-id', gerçek %s", capturedID)
	}
	if w.Header().Get("X-Request-ID") != "client-provided-id" {
		t.Error("X-Request-ID header client ID'sini yansıtmalı")
	}
}

func TestCORS_Options(t *testing.T) {
	handler := CORS(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("OPTIONS", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Errorf("OPTIONS beklenen 204, gerçek %d", w.Code)
	}
}

func TestCORS_Headers(t *testing.T) {
	handler := CORS(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("GET", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Error("Access-Control-Allow-Origin: * olmalı")
	}
	if w.Header().Get("Access-Control-Allow-Methods") == "" {
		t.Error("Access-Control-Allow-Methods eksik")
	}
}

// =============================================================================
// Context helper testleri
// =============================================================================

func TestGetTenantID_Empty(t *testing.T) {
	id := GetTenantID(context.Background())
	if id != "" {
		t.Errorf("beklenen '', gerçek %s", id)
	}
}

func TestGetTenantID_FromContext(t *testing.T) {
	ctx := context.WithValue(context.Background(), CtxKeyTenantID, "T01")
	id := GetTenantID(ctx)
	if id != "T01" {
		t.Errorf("beklenen T01, gerçek %s", id)
	}
}

func TestGetWorkspaceID_Empty(t *testing.T) {
	id := GetWorkspaceID(context.Background())
	if id != "" {
		t.Errorf("beklenen '', gerçek %s", id)
	}
}

func TestGetUserID_Empty(t *testing.T) {
	id := GetUserID(context.Background())
	if id != "" {
		t.Errorf("beklenen '', gerçek %s", id)
	}
}

func TestGetUserID_FromContext(t *testing.T) {
	ctx := context.WithValue(context.Background(), CtxKeyUserID, "U42")
	id := GetUserID(ctx)
	if id != "U42" {
		t.Errorf("beklenen U42, gerçek %s", id)
	}
}

func TestGetUserRole_Empty(t *testing.T) {
	role := GetUserRole(context.Background())
	if role != "" {
		t.Errorf("beklenen '', gerçek %s", role)
	}
}

func TestGetUserRole_FromContext(t *testing.T) {
	ctx := context.WithValue(context.Background(), ctxKeyUserRole, "admin")
	role := GetUserRole(ctx)
	if role != "admin" {
		t.Errorf("beklenen admin, gerçek %s", role)
	}
}

// =============================================================================
// RBAC: hasSufficientRole birim testleri
// =============================================================================

func TestHasSufficientRole(t *testing.T) {
	tests := []struct {
		name      string
		user, min string
		expected  bool
	}{
		{"admin ≥ viewer", "admin", "viewer", true},
		{"admin ≥ editor", "admin", "editor", true},
		{"admin ≥ admin", "admin", "admin", true},
		{"editor ≥ viewer", "editor", "viewer", true},
		{"editor ≥ editor", "editor", "editor", true},
		{"editor < admin", "editor", "admin", false},
		{"viewer < admin", "viewer", "admin", false},
		{"viewer < editor", "viewer", "editor", false},
		{"viewer ≥ viewer", "viewer", "viewer", true},
		{"unknown role -> false", "unknown", "viewer", false},
		{"unknown min role -> false", "admin", "unknown", false},
		{"empty role -> false", "", "viewer", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := hasSufficientRole(tt.user, tt.min)
			if got != tt.expected {
				t.Errorf("hasSufficientRole(%q, %q) = %v, beklenen %v", tt.user, tt.min, got, tt.expected)
			}
		})
	}
}

// =============================================================================
// RequireRole middleware testleri
// =============================================================================

// withRoleContext sets a role in the request context (simulates RequireWorkspaceAccess).
func withRoleContext(r *http.Request, role string) *http.Request {
	return r.WithContext(context.WithValue(r.Context(), ctxKeyUserRole, role))
}

func TestRequireRole_AdminCanAccessAdminRoute(t *testing.T) {
	handler := RequireRole(RoleAdmin)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := withRoleContext(httptest.NewRequest("GET", "/admin", nil), RoleAdmin)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("admin için beklenen 200, gerçek %d", w.Code)
	}
}

func TestRequireRole_EditorCanAccessEditorRoute(t *testing.T) {
	handler := RequireRole(RoleEditor)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := withRoleContext(httptest.NewRequest("GET", "/editor", nil), RoleEditor)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("editor için beklenen 200, gerçek %d", w.Code)
	}
}

func TestRequireRole_AdminCanAccessViewerRoute(t *testing.T) {
	handler := RequireRole(RoleViewer)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := withRoleContext(httptest.NewRequest("GET", "/viewer", nil), RoleAdmin)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("admin viewer route'a erişebilmeli, gerçek %d", w.Code)
	}
}

func TestRequireRole_ViewerBlockedFromAdminRoute(t *testing.T) {
	handler := RequireRole(RoleAdmin)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("handler çağrılmamalı")
	}))

	req := withRoleContext(httptest.NewRequest("GET", "/admin", nil), RoleViewer)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusForbidden {
		t.Errorf("viewer admin route için beklenen 403, gerçek %d", w.Code)
	}
}

func TestRequireRole_EditorBlockedFromAdminRoute(t *testing.T) {
	handler := RequireRole(RoleAdmin)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("handler çağrılmamalı")
	}))

	req := withRoleContext(httptest.NewRequest("GET", "/admin", nil), RoleEditor)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusForbidden {
		t.Errorf("editor admin route için beklenen 403, gerçek %d", w.Code)
	}
}

func TestRequireRole_NoRoleReturns401(t *testing.T) {
	handler := RequireRole(RoleViewer)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("handler çağrılmamalı")
	}))

	req := httptest.NewRequest("GET", "/resource", nil) // role yok
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("rol yoksa beklenen 401, gerçek %d", w.Code)
	}
}

func TestRequireRole_UnknownRoleReturnsForbidden(t *testing.T) {
	handler := RequireRole(RoleViewer)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("handler çağrılmamalı")
	}))

	req := withRoleContext(httptest.NewRequest("GET", "/resource", nil), "superadmin")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusForbidden {
		t.Errorf("tanınmayan rol için beklenen 403, gerçek %d", w.Code)
	}
}

// =============================================================================
// Authenticate middleware testleri
// =============================================================================

func TestAuthenticate_ValidToken(t *testing.T) {
	validator := func(tokenStr string) (userID, tenantID, role string, err error) {
		return "U01", "T01", "admin", nil
	}
	handler := Authenticate(validator)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		uid := GetUserID(r.Context())
		tid := GetTenantID(r.Context())
		if uid != "U01" {
			t.Errorf("beklenen U01, gerçek %s", uid)
		}
		if tid != "T01" {
			t.Errorf("beklenen T01, gerçek %s", tid)
		}
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("Authorization", "Bearer valid-token")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("geçerli token için beklenen 200, gerçek %d", w.Code)
	}
}

func TestAuthenticate_InvalidToken(t *testing.T) {
	validator := func(tokenStr string) (userID, tenantID, role string, err error) {
		return "", "", "", assertAnError{}
	}
	handler := Authenticate(validator)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("handler çağrılmamalı")
	}))

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("Authorization", "Bearer invalid-token")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("geçersiz token için beklenen 401, gerçek %d", w.Code)
	}
}

func TestAuthenticate_NoHeader(t *testing.T) {
	validator := func(tokenStr string) (userID, tenantID, role string, err error) {
		t.Error("validator çağrılmamalı")
		return "", "", "", assertAnError{}
	}
	handler := Authenticate(validator)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("handler çağrılmamalı")
	}))

	req := httptest.NewRequest("GET", "/protected", nil) // Authorization header yok
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("header yoksa beklenen 401, gerçek %d", w.Code)
	}
}

func TestAuthenticate_NoBearerPrefix(t *testing.T) {
	var capturedToken string
	validator := func(tokenStr string) (userID, tenantID, role string, err error) {
		capturedToken = tokenStr
		return "U01", "T01", "admin", nil
	}
	handler := Authenticate(validator)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("GET", "/protected", nil)
	req.Header.Set("Authorization", "raw-token-without-bearer")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if capturedToken != "raw-token-without-bearer" {
		t.Errorf("Bearer prefix olmadan da token okunmalı, gerçek: %s", capturedToken)
	}
	if w.Code != http.StatusOK {
		t.Errorf("beklenen 200, gerçek %d", w.Code)
	}
}

// assertAnError is a minimal error implementation for testing.
type assertAnError struct{}

func (assertAnError) Error() string { return "test error" }

// =============================================================================
// RequireWorkspace middleware testleri
// =============================================================================

func TestRequireWorkspace_Valid(t *testing.T) {
	handler := RequireWorkspace(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ws := GetWorkspaceID(r.Context())
		if ws != "WS42" {
			t.Errorf("beklenen WS42, gerçek %s", ws)
		}
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("GET", "/workspaces/WS42/resource", nil)
	// Chi route params'ı mock'la: URLParam chi context'ten okur
	rctx := chi.NewRouteContext()
	rctx.URLParams.Add("ws", "WS42")
	req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))

	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("geçerli workspace için beklenen 200, gerçek %d", w.Code)
	}
}

func TestRequireWorkspace_Empty(t *testing.T) {
	handler := RequireWorkspace(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("handler çağrılmamalı")
	}))

	req := httptest.NewRequest("GET", "/workspaces//resource", nil)
	// Chi route param'ı boş bırak

	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("boş workspace için beklenen 400, gerçek %d", w.Code)
	}
}

// =============================================================================
// İzolasyon: Context middleware chain testleri
// =============================================================================

func TestMiddlewareChain_ContextFlow(t *testing.T) {
	// Bu test, middleware zincirinin context'i doğru şekilde aktardığını doğrular.
	// Zincir: RequestID → Logger (passthrough) → Authenticate (mock)

	tokenValidator := func(token string) (string, string, string, error) {
		return "U01", "T01", "editor", nil
	}

	var finalRequestID, finalUserID, finalTenantID string

	handler := RequestID(
		Logger(
			Authenticate(tokenValidator)(
				http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					finalRequestID = GetRequestID(r.Context())
					finalUserID = GetUserID(r.Context())
					finalTenantID = GetTenantID(r.Context())
					w.WriteHeader(http.StatusOK)
				}),
			),
		),
	)

	req := httptest.NewRequest("GET", "/test", nil)
	req.Header.Set("Authorization", "Bearer some-token")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("beklenen 200, gerçek %d", w.Code)
	}
	if finalRequestID == "" {
		t.Error("RequestID middleware'den geçmeli")
	}
	if finalUserID != "U01" {
		t.Errorf("beklenen U01, gerçek %s", finalUserID)
	}
	if finalTenantID != "T01" {
		t.Errorf("beklenen T01, gerçek %s", finalTenantID)
	}
}

func TestMiddlewareChain_RBACBlocking(t *testing.T) {
	// Bu test, Authenticate sonrası RequireRole'un JWT claim'indeki rolü
	// kullanarak yetkisiz erişimi bloke ettiğini doğrular.
	// Authenticate token'dan rolü context'e taşır (JWT claim → context),
	// böylece workspace dışı rotalarda (R-serisi vb.) RBAC çalışır.

	tokenValidator := func(token string) (string, string, string, error) {
		return "U01", "T01", "viewer", nil
	}

	var handlerCalled bool

	handler := Authenticate(tokenValidator)(
		RequireRole(RoleAdmin)(
			http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				handlerCalled = true
				w.WriteHeader(http.StatusOK)
			}),
		),
	)

	req := httptest.NewRequest("GET", "/admin", nil)
	req.Header.Set("Authorization", "Bearer token")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	// Viewer admin route'una erişemez: kimliği doğrulanmış ama yetkisiz → 403
	if w.Code != http.StatusForbidden {
		t.Errorf("viewer admin route için beklenen 403, gerçek %d", w.Code)
	}
	if handlerCalled {
		t.Error("handler bloke edilmeliydi")
	}
}

func TestMiddlewareChain_AuthenticatePropagatesRole(t *testing.T) {
	// Bu test, Authenticate'in JWT claim'indeki rolü context'e taşıdığını
	// ve RequireRole'un workspace dışı rotalarda bu rolü kullanabildiğini doğrular.

	tokenValidator := func(token string) (string, string, string, error) {
		return "U01", "T01", "editor", nil
	}

	var handlerCalled bool

	handler := Authenticate(tokenValidator)(
		RequireRole(RoleEditor)(
			http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				handlerCalled = true
				w.WriteHeader(http.StatusOK)
			}),
		),
	)

	req := httptest.NewRequest("GET", "/editor", nil)
	req.Header.Set("Authorization", "Bearer token")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("editor editor route'a erişebilmeli, beklenen 200, gerçek %d", w.Code)
	}
	if !handlerCalled {
		t.Error("handler çağrılmalıydı")
	}
}

func TestMiddlewareChain_RequireWorkspaceAccessIsolation(t *testing.T) {
	// Bu test, RequireWorkspaceAccess'in workspace'e üye olmayan kullanıcıyı
	// bloke ettiğini doğrular. DB sorgusu mock'lanamadığından (concrete *db.Pool),
	// bu test testcontainers ile çalışan bir entegrasyon testidir.
	//
	// Çalıştırmak için: go test -tags=integration -run TestIsolationIntegration ./...
	t.Skip("testcontainers gerektirir: go test -tags=integration -run TestIsolationIntegration ./...")

	// İzolasyon katmanları:
	//   Katman 1: RLS — tüm tablolarda tenant_id filter (migrations/001_initial.sql)
	//   Katman 2: TenantContext — app.tenant_id SET LOCAL
	//   Katman 3: RequireWorkspaceAccess — membership row sorgusu
	//   Katman 4: RequireRole — rol hiyerarşisi
	//
	// Negatif senaryolar (integration test):
	//   1. Tenant A kullanıcısı Tenant B verisine erişemez (RLS)
	//   2. WS A üyesi WS B endpoint'ine erişemez (RequireWorkspaceAccess)
	//   3. Silinmiş/askıya alınmış kullanıcı erişemez (membership yok)
	//   4. Editor admin route'una erişemez (RequireRole)
	_ = t // placeholder: buraya testcontainers setup + HTTP testleri eklenecek
}
