package storage

import (
	"testing"
)

func TestNewEncryptedClient_InvalidKey(t *testing.T) {
	_, err := NewEncryptedClient(nil, "too-short")
	if err == nil {
		t.Fatal("expected error for short master key")
	}
}

func TestNewEncryptedClient_ValidKey(t *testing.T) {
	key := "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	_, err := NewEncryptedClient(nil, key)
	if err != nil {
		t.Fatalf("NewEncryptedClient failed: %v", err)
	}
}

func TestNewEncryptedClient_InvalidHex(t *testing.T) {
	_, err := NewEncryptedClient(nil, "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz")
	if err == nil {
		t.Fatal("expected error for invalid hex key")
	}
}

func TestGenerateMasterKey(t *testing.T) {
	key, err := GenerateMasterKey()
	if err != nil {
		t.Fatalf("GenerateMasterKey failed: %v", err)
	}
	if len(key) != 64 {
		t.Errorf("expected 64 hex chars, got %d: %s", len(key), key)
	}
}

func TestGenerateTenantKey(t *testing.T) {
	key, err := GenerateTenantKey()
	if err != nil {
		t.Fatalf("GenerateTenantKey failed: %v", err)
	}
	if len(key) != 32 {
		t.Errorf("expected 32 bytes, got %d", len(key))
	}

	key2, err := GenerateTenantKey()
	if err != nil {
		t.Fatalf("GenerateTenantKey failed: %v", err)
	}
	if bytesEqual(key, key2) {
		t.Fatal("two generated keys should be different")
	}
}

func TestDeriveMasterKeyFromEnv(t *testing.T) {
	key := DeriveMasterKeyFromEnv("test-jwt-secret")
	if len(key) != 64 {
		t.Errorf("expected 64 hex chars, got %d", len(key))
	}

	key2 := DeriveMasterKeyFromEnv("test-jwt-secret")
	if key != key2 {
		t.Fatal("same input should produce same output")
	}

	key3 := DeriveMasterKeyFromEnv("different-secret")
	if key == key3 {
		t.Fatal("different input should produce different output")
	}
}

func TestEncryptAESGCM_Decrypt(t *testing.T) {
	key := make([]byte, 32)
	for i := range key {
		key[i] = byte(i)
	}

	plaintext := []byte("GeoLens test verisi")
	ciphertext, err := encryptAESGCM(key, plaintext)
	if err != nil {
		t.Fatalf("encryptAESGCM failed: %v", err)
	}

	if len(ciphertext) == 0 {
		t.Fatal("ciphertext should not be empty")
	}

	decrypted, err := decryptAESGCM(key, ciphertext)
	if err != nil {
		t.Fatalf("decryptAESGCM failed: %v", err)
	}

	if !bytesEqual(decrypted, plaintext) {
		t.Errorf("decrypted text mismatch: got %s, expected %s", decrypted, plaintext)
	}
}

func TestEncryptAESGCM_Empty(t *testing.T) {
	key := make([]byte, 32)
	for i := range key {
		key[i] = byte(i)
	}

	ciphertext, err := encryptAESGCM(key, []byte{})
	if err != nil {
		t.Fatalf("encryptAESGCM failed: %v", err)
	}

	decrypted, err := decryptAESGCM(key, ciphertext)
	if err != nil {
		t.Fatalf("decryptAESGCM failed: %v", err)
	}

	if len(decrypted) != 0 {
		t.Fatal("decrypted should be empty")
	}
}

func TestDecrypt_InvalidData(t *testing.T) {
	key := make([]byte, 32)
	for i := range key {
		key[i] = byte(i)
	}

	_, err := decryptAESGCM(key, []byte("invalid-data"))
	if err == nil {
		t.Fatal("expected error for invalid ciphertext")
	}
}

func TestEncryptAESGCM_DifferentKeys(t *testing.T) {
	key1 := make([]byte, 32)
	key2 := make([]byte, 32)
	key2[0] = 1

	plaintext := []byte("sensitive data")
	ciphertext, err := encryptAESGCM(key1, plaintext)
	if err != nil {
		t.Fatalf("encryptAESGCM failed: %v", err)
	}

	_, err = decryptAESGCM(key2, ciphertext)
	if err == nil {
		t.Fatal("expected error when decrypting with different key")
	}
}

func TestTenantKeyPath(t *testing.T) {
	path := tenantKeyPath("tenant-1")
	if path == "" {
		t.Fatal("tenant key path should not be empty")
	}
}

func bytesEqual(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
