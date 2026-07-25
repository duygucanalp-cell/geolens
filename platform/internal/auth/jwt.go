package auth

import (
	"context"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"

	"github.com/geolens/platform/platform/httpmw"
)

const blacklistPrefix = "token:blacklist:"

// Claims represents the JWT claims for GeoLens platform tokens.
type Claims struct {
	jwt.RegisteredClaims
	UserID   string `json:"user_id"`
	TenantID string `json:"tenant_id"`
	Role     string `json:"role"`
}

// JWTService handles JWT token operations.
type JWTService struct {
	secret   []byte
	tokenTTL time.Duration
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

// TokenValidator returns an httpmw.TokenValidator function that wraps JWTService.ValidateToken
// with Redis blacklist checking. If rdb is nil, blacklist check is skipped.
func (s *JWTService) TokenValidator(rdb *redis.Client) func(string) (string, string, string, error) {
	return func(tokenStr string) (string, string, string, error) {
		claims, err := s.ValidateToken(tokenStr)
		if err != nil {
			return "", "", "", err
		}

		if rdb != nil && claims.ID != "" {
			exists, err := rdb.Exists(context.Background(), blacklistPrefix+claims.ID).Result()
			if err == nil && exists > 0 {
				return "", "", "", fmt.Errorf("token iptal edilmiş")
			}
		}

		return claims.UserID, claims.TenantID, claims.Role, nil
	}
}

// BlacklistToken adds the given JWT token to the Redis blacklist for its remaining lifetime.
// The blacklist key is token:blacklist:{jti} and auto-expires when the token would have expired.
func (s *JWTService) BlacklistToken(tokenStr string, rdb *redis.Client) error {
	if rdb == nil {
		return nil
	}

	claims, err := s.ValidateToken(tokenStr)
	if err != nil {
		return fmt.Errorf("blacklist için token doğrulama: %w", err)
	}

	remaining := time.Until(claims.ExpiresAt.Time)
	if remaining <= 0 {
		return nil
	}

	return rdb.Set(context.Background(), blacklistPrefix+claims.ID, "1", remaining).Err()
}
