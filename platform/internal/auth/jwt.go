package auth

import (
	"context"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"

	"github.com/geolens/platform/platform/httpmw"
)

// Claims represents the JWT claims for GeoLens platform tokens.
type Claims struct {
	jwt.RegisteredClaims
	UserID   string `json:"user_id"`
	TenantID string `json:"tenant_id"`
	Role     string `json:"role"`
}

// JWTService handles JWT token operations.
type JWTService struct {
	secret     []byte
	tokenTTL   time.Duration
}

// NewJWTService creates a new JWT service.
func NewJWTService(secret string) *JWTService {
	ttl := 2 * time.Hour // kayan süre (D-28)
	return &JWTService{
		secret:   []byte(secret),
		tokenTTL: ttl,
	}
}

// GenerateToken creates a signed JWT for a user session.
func (s *JWTService) GenerateToken(userID, tenantID, role string) (string, time.Time, error) {
	now := time.Now()
	expiresAt := now.Add(s.tokenTTL)

	claims := &Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			ID:        uuid.New().String(),
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(expiresAt),
			Subject:   userID,
		},
		UserID:   userID,
		TenantID: tenantID,
		Role:     role,
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signedToken, err := token.SignedString(s.secret)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("jwt imzalama: %w", err)
	}

	return signedToken, expiresAt, nil
}

// ValidateToken parses and validates a JWT token.
func (s *JWTService) ValidateToken(tokenStr string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("beklenmeyen imzalama yöntemi: %v", t.Header["alg"])
		}
		return s.secret, nil
	})
	if err != nil {
		return nil, fmt.Errorf("jwt doğrulama: %w", err)
	}

	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		return nil, fmt.Errorf("geçersiz token claims")
	}

	return claims, nil
}

// InjectContext adds user auth context to the request context.
func InjectContext(ctx context.Context, claims *Claims) context.Context {
	ctx = context.WithValue(ctx, httpmw.CtxKeyUserID, claims.UserID)
	ctx = context.WithValue(ctx, httpmw.CtxKeyTenantID, claims.TenantID)
	return ctx
}

// TokenValidator returns an httpmw.TokenValidator function that wraps JWTService.ValidateToken.
// Bu, httpmw'nin auth paketine import bağımlılığı olmadan JWT doğrulaması yapmasını sağlar.
func (s *JWTService) TokenValidator() func(string) (string, string, string, error) {
	return func(tokenStr string) (string, string, string, error) {
		claims, err := s.ValidateToken(tokenStr)
		if err != nil {
			return "", "", "", err
		}
		return claims.UserID, claims.TenantID, claims.Role, nil
	}
}
