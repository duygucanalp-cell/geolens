package auth

import (
	"context"
	"testing"
	"time"

	"github.com/geolens/platform/platform/httpmw"
)

func TestNewJWTService(t *testing.T) {
	svc := NewJWTService("test-secret")
	if svc == nil {
		t.Fatal("JWTService should not be nil")
	}
}

func TestGenerateToken_Success(t *testing.T) {
	svc := NewJWTService("test-secret")
	token, expiresAt, err := svc.GenerateToken("user-1", "tenant-1", "admin")
	if err != nil {
		t.Fatalf("GenerateToken failed: %v", err)
	}
	if token == "" {
		t.Fatal("token should not be empty")
	}
	if expiresAt.IsZero() {
		t.Fatal("expiresAt should not be zero")
	}
	if time.Until(expiresAt) > 3*time.Hour {
		t.Fatal("token expiry should be ~2 hours")
	}
}

func TestValidateToken_Valid(t *testing.T) {
	svc := NewJWTService("test-secret")
	tokenStr, _, err := svc.GenerateToken("user-1", "tenant-1", "admin")
	if err != nil {
		t.Fatalf("GenerateToken failed: %v", err)
	}

	claims, err := svc.ValidateToken(tokenStr)
	if err != nil {
		t.Fatalf("ValidateToken failed: %v", err)
	}
	if claims.UserID != "user-1" {
		t.Errorf("expected user-1, got %s", claims.UserID)
	}
	if claims.TenantID != "tenant-1" {
		t.Errorf("expected tenant-1, got %s", claims.TenantID)
	}
	if claims.Role != "admin" {
		t.Errorf("expected admin, got %s", claims.Role)
	}
}

func TestValidateToken_InvalidSignature(t *testing.T) {
	svc := NewJWTService("test-secret")
	otherSvc := NewJWTService("other-secret")
	tokenStr, _, err := otherSvc.GenerateToken("user-1", "tenant-1", "admin")
	if err != nil {
		t.Fatalf("GenerateToken failed: %v", err)
	}

	_, err = svc.ValidateToken(tokenStr)
	if err == nil {
		t.Fatal("expected validation error for wrong signature")
	}
}

func TestValidateToken_Malformed(t *testing.T) {
	svc := NewJWTService("test-secret")
	_, err := svc.ValidateToken("not-a-jwt-token")
	if err == nil {
		t.Fatal("expected error for malformed token")
	}
}

func TestValidateToken_Empty(t *testing.T) {
	svc := NewJWTService("test-secret")
	_, err := svc.ValidateToken("")
	if err == nil {
		t.Fatal("expected error for empty token")
	}
}

func TestInjectContext(t *testing.T) {
	claims := &Claims{
		UserID:   "user-1",
		TenantID: "tenant-1",
		Role:     "admin",
	}

	ctx := InjectContext(context.Background(), claims)
	if ctx == nil {
		t.Fatal("context should not be nil")
	}
}

func TestInjectContext_Values(t *testing.T) {
	claims := &Claims{
		UserID:   "user-1",
		TenantID: "tenant-1",
		Role:     "admin",
	}

	ctx := InjectContext(context.Background(), claims)
	userID := httpmw.GetUserID(ctx)
	if userID != "user-1" {
		t.Errorf("expected user-1, got %s", userID)
	}
	tenantID := httpmw.GetTenantID(ctx)
	if tenantID != "tenant-1" {
		t.Errorf("expected tenant-1, got %s", tenantID)
	}
}

func TestTokenValidator(t *testing.T) {
	svc := NewJWTService("test-secret")
	validator := svc.TokenValidator(nil)

	tokenStr, _, err := svc.GenerateToken("user-1", "tenant-1", "editor")
	if err != nil {
		t.Fatalf("GenerateToken failed: %v", err)
	}

	userID, tenantID, role, err := validator(context.Background(), tokenStr)
	if err != nil {
		t.Fatalf("validator failed: %v", err)
	}
	if userID != "user-1" {
		t.Errorf("expected user-1, got %s", userID)
	}
	if tenantID != "tenant-1" {
		t.Errorf("expected tenant-1, got %s", tenantID)
	}
	if role != "editor" {
		t.Errorf("expected editor, got %s", role)
	}
}

func TestTokenValidator_Invalid(t *testing.T) {
	svc := NewJWTService("test-secret")
	validator := svc.TokenValidator(nil)

	_, _, _, err := validator(context.Background(), "invalid-token")
	if err == nil {
		t.Fatal("expected error for invalid token")
	}
}

func TestGenerateToken_DifferentSecrets(t *testing.T) {
	svc1 := NewJWTService("secret-1")
	svc2 := NewJWTService("secret-2")

	token1, _, err := svc1.GenerateToken("user-1", "tenant-1", "admin")
	if err != nil {
		t.Fatalf("GenerateToken failed: %v", err)
	}

	_, err = svc2.ValidateToken(token1)
	if err == nil {
		t.Fatal("expected validation error for different secrets")
	}
}

func TestGenerateToken_UniqueIDs(t *testing.T) {
	svc := NewJWTService("test-secret")
	token1, _, err := svc.GenerateToken("user-1", "tenant-1", "admin")
	if err != nil {
		t.Fatalf("first GenerateToken failed: %v", err)
	}
	token2, _, err := svc.GenerateToken("user-1", "tenant-1", "admin")
	if err != nil {
		t.Fatalf("second GenerateToken failed: %v", err)
	}
	if token1 == token2 {
		t.Fatal("tokens should be unique (different JWT IDs)")
	}
}
