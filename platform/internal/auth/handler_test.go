package auth

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
	"github.com/geolens/platform/platform/httpmw"
	"golang.org/x/crypto/bcrypt"
)

func newTestHandler() *Handler {
	return NewHandler(&testutil.MockPool{}, nil, nil, nil, "")
}

// mockMailSender, davet e-postası gönderimini test etmek için kullanılan sahte göndericidir.
type mockMailSender struct {
	sent []string
}

func (m *mockMailSender) SendEmail(to, subject, htmlContent string) error {
	m.sent = append(m.sent, to)
	return nil
}

func TestInviteMember_SendsInvitationEmail(t *testing.T) {
	mail := &mockMailSender{}
	h := NewHandler(&testutil.MockPool{}, nil, nil, mail, "https://app.geolens.ai")

	ctx := context.WithValue(context.Background(), httpmw.CtxKeyTenantID, "T01")
	ctx = context.WithValue(ctx, httpmw.CtxKeyUserID, "U42")

	req := httptest.NewRequest(http.MethodPost, "/v1/tenant/invitations", bytes.NewReader([]byte(
		`{"email":"yonetici@example.com","workspace_id":"WS1","role":"editor"}`)))
	req.Header.Set("Content-Type", "application/json")
	req = req.WithContext(ctx)
	w := httptest.NewRecorder()

	h.InviteMember(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}

	var body map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("yanıt ayrıştırılamadı: %v", err)
	}
	if body["email_sent"] != true {
		t.Fatalf("expected email_sent=true, got %v", body["email_sent"])
	}
	if token, _ := body["token"].(string); token == "" {
		t.Fatalf("expected token dönsün")
	}
	if len(mail.sent) != 1 || mail.sent[0] != "yonetici@example.com" {
		t.Fatalf("e-posta gönderilmedi: %v", mail.sent)
	}
}

func TestInviteMember_MockModeSkipsEmail(t *testing.T) {
	h := NewHandler(&testutil.MockPool{}, nil, nil, nil, "")

	ctx := context.WithValue(context.Background(), httpmw.CtxKeyTenantID, "T01")
	ctx = context.WithValue(ctx, httpmw.CtxKeyUserID, "U42")

	req := httptest.NewRequest(http.MethodPost, "/v1/tenant/invitations", strings.NewReader(
		`{"email":"kisi@example.com","workspace_id":"WS1","role":"viewer"}`))
	req.Header.Set("Content-Type", "application/json")
	req = req.WithContext(ctx)
	w := httptest.NewRecorder()

	h.InviteMember(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}

	var body map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("yanıt ayrıştırılamadı: %v", err)
	}
	if body["email_sent"] != false {
		t.Fatalf("expected email_sent=false, got %v", body["email_sent"])
	}
}

func TestRegister_InvalidJSON(t *testing.T) {
	h := newTestHandler()

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/register", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Register(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestRegister_MissingFields(t *testing.T) {
	h := newTestHandler()

	tests := []struct {
		name string
		body map[string]string
	}{
		{name: "empty email", body: map[string]string{"password": "12345678", "name": "Test"}},
		{name: "empty password", body: map[string]string{"email": "test@test.com", "name": "Test"}},
		{name: "empty name", body: map[string]string{"email": "test@test.com", "password": "12345678"}},
		{name: "all empty", body: map[string]string{}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			req := httptest.NewRequest(http.MethodPost, "/v1/auth/register", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()

			h.Register(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusBadRequest {
				t.Fatalf("expected 400, got %d", resp.StatusCode)
			}
		})
	}
}

func TestRegister_ShortPassword(t *testing.T) {
	h := newTestHandler()

	body, _ := json.Marshal(map[string]string{
		"email":    "test@test.com",
		"password": "123",
		"name":     "Test",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Register(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestLogin_InvalidJSON(t *testing.T) {
	h := newTestHandler()

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/login", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Login(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestLogin_MissingFields(t *testing.T) {
	h := newTestHandler()

	tests := []struct {
		name string
		body map[string]string
	}{
		{name: "empty email", body: map[string]string{"password": "12345678"}},
		{name: "empty password", body: map[string]string{"email": "test@test.com"}},
		{name: "all empty", body: map[string]string{}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			req := httptest.NewRequest(http.MethodPost, "/v1/auth/login", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()

			h.Login(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusBadRequest {
				t.Fatalf("expected 400, got %d", resp.StatusCode)
			}
		})
	}
}

func TestLogout(t *testing.T) {
	h := newTestHandler()

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/logout", nil)
	w := httptest.NewRecorder()

	h.Logout(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
}

func TestRefresh_RequiresToken(t *testing.T) {
	h := newTestHandler()
	h.jwt = NewJWTService("test-secret")

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/refresh", nil)
	w := httptest.NewRecorder()

	h.Refresh(w, req)

	if w.Result().StatusCode != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Result().StatusCode)
	}
}

func TestRefresh_InvalidToken(t *testing.T) {
	h := newTestHandler()
	h.jwt = NewJWTService("test-secret")

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/refresh", nil)
	req.Header.Set("Authorization", "Bearer not-a-jwt")
	w := httptest.NewRecorder()

	h.Refresh(w, req)

	if w.Result().StatusCode != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Result().StatusCode)
	}
}

func TestRefresh_SlidesSession(t *testing.T) {
	h := newTestHandler()
	jwtSvc := NewJWTService("test-secret")
	h.jwt = jwtSvc

	oldToken, _, err := jwtSvc.GenerateToken("U1", "T1", "admin")
	if err != nil {
		t.Fatal(err)
	}

	// Rol DB'den taze okunur — admin claim'i olsa da DB 'editor' der
	h.pool = &testutil.MockPool{
		QueryRowFunc: func(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"editor"}}
		},
	}

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/refresh", nil)
	req.Header.Set("Authorization", "Bearer "+oldToken)
	w := httptest.NewRecorder()

	h.Refresh(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	var body authResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("yanıt ayrıştırılamadı: %v", err)
	}
	if body.Token == "" || body.Token == oldToken {
		t.Fatalf("yeni token bekleniyordu")
	}
	if body.Role != "editor" {
		t.Fatalf("expected role editor (DB'den), got %q", body.Role)
	}
	if body.ExpiresAt == "" {
		t.Fatalf("expires_at boş olmamalı")
	}
}

func TestResolveRBACRole(t *testing.T) {
	tests := []struct {
		name       string
		membership string
		queryErr   bool
		fallback   string
		want       string
	}{
		{name: "üyelik admin", membership: "admin", want: "admin"},
		{name: "üyelik editor", membership: "editor", want: "editor"},
		{name: "üyelik viewer", membership: "viewer", want: "viewer"},
		{name: "üyelik member (legacy) → viewer", membership: "member", want: "viewer"},
		{name: "üyelik yok + fallback admin", queryErr: true, fallback: "admin", want: "admin"},
		{name: "üyelik yok + fallback member → viewer", queryErr: true, fallback: "member", want: "viewer"},
		{name: "üyelik yok + fallback boş → viewer", queryErr: true, fallback: "", want: "viewer"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			pool := &testutil.MockPool{
				QueryRowFunc: func(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
					if tt.queryErr {
						return &testutil.MockRow{Err: fmt.Errorf("satır bulunamadı")}
					}
					return &testutil.MockRow{Values: []any{tt.membership}}
				},
			}
			h := NewHandler(pool, nil, nil, nil, "")
			got := h.resolveRBACRole(context.Background(), "U1", "T1", tt.fallback)
			if got != tt.want {
				t.Errorf("resolveRBACRole = %q, want %q", got, tt.want)
			}
		})
	}
}

// TestLogin_ResolvesRBACRoleFromMembership — identity.users.role 'member' olsa bile
// JWT claim'i üyelikten (config.memberships) çözülür; tenant-level rotalar (guardrails
// vb.) artık 401 authentication_required / 403 almaz.
func TestLogin_ResolvesRBACRoleFromMembership(t *testing.T) {
	h := newTestHandler()
	h.jwt = NewJWTService("test-secret")

	hashedPW, err := bcrypt.GenerateFromPassword([]byte("12345678"), bcrypt.MinCost)
	if err != nil {
		t.Fatalf("bcrypt hash: %v", err)
	}

	queryCalls := 0
	h.pool = &testutil.MockPool{
		QueryRowFunc: func(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
			queryCalls++
			switch queryCalls {
			case 1: // kullanıcı (ana sorgu): users.role = 'member'
				return &testutil.MockRow{Values: []any{"U1", "T1", string(hashedPW), "member"}}
			case 2: // ilk workspace
				return &testutil.MockRow{Values: []any{"WS1"}}
			default: // üyelik rolü
				return &testutil.MockRow{Values: []any{"editor"}}
			}
		},
	}

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/login", bytes.NewReader([]byte(`{"email":"x@y.com","password":"12345678"}`)))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Login(w, req)

	if queryCalls != 3 {
		t.Fatalf("expected 3 QueryRow calls (user, workspace, membership), got %d", queryCalls)
	}

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var auth authResponse
	if err := json.NewDecoder(resp.Body).Decode(&auth); err != nil {
		t.Fatalf("yanıt ayrıştırılamadı: %v", err)
	}
	if auth.Role != "editor" {
		t.Fatalf("expected role editor (membership'ten), got %q", auth.Role)
	}
	// Token claim'i de aynı rolü taşımalı
	claims, err := h.jwt.ValidateToken(auth.Token)
	if err != nil {
		t.Fatalf("token doğrulanamadı: %v", err)
	}
	if claims.Role != "editor" {
		t.Fatalf("expected claim role editor, got %q", claims.Role)
	}
}

func TestRefresh_InactiveUserRejected(t *testing.T) {
	h := newTestHandler()
	jwtSvc := NewJWTService("test-secret")
	h.jwt = jwtSvc

	oldToken, _, err := jwtSvc.GenerateToken("U1", "T1", "admin")
	if err != nil {
		t.Fatal(err)
	}

	// DB hatası (kullanıcı silinmiş / deaktif) → yenileme reddedilir
	h.pool = &testutil.MockPool{
		QueryRowFunc: func(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: fmt.Errorf("satır bulunamadı")}
		},
	}

	req := httptest.NewRequest(http.MethodPost, "/v1/auth/refresh", nil)
	req.Header.Set("Authorization", "Bearer "+oldToken)
	w := httptest.NewRecorder()

	h.Refresh(w, req)

	if w.Result().StatusCode != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Result().StatusCode)
	}
}
