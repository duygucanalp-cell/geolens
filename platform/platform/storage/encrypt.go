package storage

import (
	"bytes"
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"time"

	"github.com/minio/minio-go/v7"
)

// =============================================================================
// Kripto-silme (Crypto-shredding) Altyapısı
// =============================================================================
// Bu paket, tenant verilerini S3'te şifrelemek ve tenant silindiğinde
// şifreleme anahtarını silerek veriyi okunamaz hale getirmek için kullanılır.
//
// Konsept:
//   1. Her tenant için rastgele bir zarf anahtarı (envelope key) oluşturulur
//   2. Veri, tenant anahtarı ile AES-256-GCM şifrelenir
//   3. Tenant anahtarı, master key ile şifrelenerek saklanır
//   4. Tenant silindiğinde: zarf anahtarı silinir → veri okunamaz hale gelir
// =============================================================================

// EncryptedClient wraps a Client with tenant-level encryption support.
type EncryptedClient struct {
	*Client
	masterKey []byte // 32-byte master key for envelope encryption
}

// NewEncryptedClient creates a new encrypted storage client.
// masterKeyHex: 64-character hex string (32 bytes)
func NewEncryptedClient(client *Client, masterKeyHex string) (*EncryptedClient, error) {
	if len(masterKeyHex) != 64 {
		return nil, fmt.Errorf("master key 64 hex karakter olmalıdır (32 bayt)")
	}

	masterKey, err := hex.DecodeString(masterKeyHex)
	if err != nil {
		return nil, fmt.Errorf("master key decode: %w", err)
	}

	return &EncryptedClient{
		Client:    client,
		masterKey: masterKey,
	}, nil
}

// GenerateMasterKey generates a new random 32-byte master key.
func GenerateMasterKey() (string, error) {
	key := make([]byte, 32)
	if _, err := rand.Read(key); err != nil {
		return "", fmt.Errorf("master key oluşturma: %w", err)
	}
	return hex.EncodeToString(key), nil
}

// GenerateTenantKey generates a new random tenant envelope key.
func GenerateTenantKey() ([]byte, error) {
	key := make([]byte, 32)
	if _, err := rand.Read(key); err != nil {
		return nil, fmt.Errorf("tenant key oluşturma: %w", err)
	}
	return key, nil
}

// tenantKeyPath returns the S3 key path for a tenant's encrypted envelope key.
// Pattern: keys/{tenant}/{date}/{hash}.enk
func tenantKeyPath(tenantID string) string {
	now := time.Now()
	hash := sha256.Sum256([]byte(tenantID + now.Format("2006/01/02")))
	return fmt.Sprintf("keys/%s/%s/%x.enk",
		tenantID,
		now.Format("2006/01/02"),
		hash[:8],
	)
}

// envelopeData represents an encrypted tenant key stored in S3.
type envelopeData struct {
	TenantID     string `json:"tenant_id"`
	EncryptedKey string `json:"encrypted_key"` // AES-256-GCM encrypted tenant key (base64)
	Nonce        string `json:"nonce"`         // GCM nonce (base64)
	CreatedAt    string `json:"created_at"`
}

// SaveEncryptedRawResponse encrypts data with tenant key and saves to S3.
func (ec *EncryptedClient) SaveEncryptedRawResponse(ctx context.Context, tenantID, workspaceID, engineName string, data []byte) (string, error) {
	// Tenant anahtarını al (yoksa oluştur)
	tenantKey, err := ec.getOrCreateTenantKey(ctx, tenantID)
	if err != nil {
		return "", fmt.Errorf("tenant key alınamadı: %w", err)
	}

	// Veriyi şifrele
	encrypted, err := encryptAESGCM(tenantKey, data)
	if err != nil {
		return "", fmt.Errorf("veri şifreleme: %w", err)
	}

	// Şifrelenmiş veriyi S3'e kaydet (prefix: encrypted/)
	now := time.Now()
	key := fmt.Sprintf("encrypted/%s/%s/%s/%s/%s.json",
		tenantID,
		workspaceID,
		engineName,
		now.Format("2006/01/02"),
		fmt.Sprintf("%x", now.UnixNano())[:16],
	)

	_, err = ec.mc.PutObject(ctx, ec.bucket, key,
		bytes.NewReader(encrypted),
		int64(len(encrypted)),
		minio.PutObjectOptions{
			ContentType: "application/octet-stream",
		},
	)
	if err != nil {
		return "", fmt.Errorf("s3 şifreli kaydetme: %w", err)
	}

	return key, nil
}

// GetEncryptedRawResponse retrieves and decrypts data from S3.
func (ec *EncryptedClient) GetEncryptedRawResponse(ctx context.Context, key, tenantID string) ([]byte, error) {
	// Tenant anahtarını al
	tenantKey, err := ec.getOrCreateTenantKey(ctx, tenantID)
	if err != nil {
		return nil, fmt.Errorf("tenant key alınamadı: %w", err)
	}

	// S3'ten oku
	obj, err := ec.mc.GetObject(ctx, ec.bucket, key, minio.GetObjectOptions{})
	if err != nil {
		return nil, fmt.Errorf("s3 okuma: %w", err)
	}
	defer obj.Close()

	encrypted, err := io.ReadAll(obj)
	if err != nil {
		return nil, fmt.Errorf("s3 veri okuma: %w", err)
	}

	// Çöz
	decrypted, err := decryptAESGCM(tenantKey, encrypted)
	if err != nil {
		return nil, fmt.Errorf("veri çözme: %w", err)
	}

	return decrypted, nil
}

// DeleteTenantKey deletes a tenant's envelope key from S3 (crypto-shredding).
// Bu işlemden sonra tenant verileri OKUNAMAZ hale gelir.
func (ec *EncryptedClient) DeleteTenantKey(ctx context.Context, tenantID string) error {
	// Tenant'ın tüm anahtarlarını bul ve sil
	objectsCh := make(chan minio.ObjectInfo)

	go func() {
		defer close(objectsCh)
		for obj := range ec.mc.ListObjects(ctx, ec.bucket, minio.ListObjectsOptions{
			Prefix:    fmt.Sprintf("keys/%s/", tenantID),
			Recursive: true,
		}) {
			objectsCh <- obj
		}
	}()

	for obj := range objectsCh {
		if err := ec.mc.RemoveObject(ctx, ec.bucket, obj.Key, minio.RemoveObjectOptions{}); err != nil {
			return fmt.Errorf("anahtar silme: %s: %w", obj.Key, err)
		}
	}

	return nil
}

// getOrCreateTenantKey retrieves an existing tenant key or creates a new one.
func (ec *EncryptedClient) getOrCreateTenantKey(ctx context.Context, tenantID string) ([]byte, error) {
	// En son anahtarı bul
	objectsCh := ec.mc.ListObjects(ctx, ec.bucket, minio.ListObjectsOptions{
		Prefix:    fmt.Sprintf("keys/%s/", tenantID),
		Recursive: true,
		MaxKeys:   1,
	})

	for obj := range objectsCh {
		// Anahtar bulundu, çöz ve döndür
		objData, err := ec.mc.GetObject(ctx, ec.bucket, obj.Key, minio.GetObjectOptions{})
		if err != nil {
			break // Anahtar bozulmuş olabilir, yeni oluştur
		}
		defer objData.Close()

		data, err := io.ReadAll(objData)
		if err != nil {
			break
		}

		var env envelopeData
		if err := json.Unmarshal(data, &env); err != nil {
			break
		}

		nonce, err := base64.StdEncoding.DecodeString(env.Nonce)
		if err != nil {
			break
		}

		encKey, err := base64.StdEncoding.DecodeString(env.EncryptedKey)
		if err != nil {
			break
		}

		// Master key ile çöz
		block, err := aes.NewCipher(ec.masterKey)
		if err != nil {
			return nil, fmt.Errorf("aes cipher: %w", err)
		}

		aesGCM, err := cipher.NewGCM(block)
		if err != nil {
			return nil, fmt.Errorf("gcm: %w", err)
		}

		tenantKey, err := aesGCM.Open(nil, nonce, encKey, nil)
		if err != nil {
			return nil, fmt.Errorf("tenant key çözme: %w", err)
		}

		return tenantKey, nil
	}

	// Yeni tenant anahtarı oluştur
	tenantKey, err := GenerateTenantKey()
	if err != nil {
		return nil, err
	}

	// Tenant anahtarını master key ile şifrele
	block, err := aes.NewCipher(ec.masterKey)
	if err != nil {
		return nil, fmt.Errorf("aes cipher: %w", err)
	}

	aesGCM, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("gcm: %w", err)
	}

	nonce := make([]byte, aesGCM.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, fmt.Errorf("nonce: %w", err)
	}

	encryptedKey := aesGCM.Seal(nil, nonce, tenantKey, nil)

	env := envelopeData{
		TenantID:     tenantID,
		EncryptedKey: base64.StdEncoding.EncodeToString(encryptedKey),
		Nonce:        base64.StdEncoding.EncodeToString(nonce),
		CreatedAt:    time.Now().UTC().Format(time.RFC3339),
	}

	envJSON, err := json.Marshal(env)
	if err != nil {
		return nil, fmt.Errorf("envelope marshal: %w", err)
	}

	keyPath := tenantKeyPath(tenantID)
	_, err = ec.mc.PutObject(ctx, ec.bucket, keyPath,
		bytes.NewReader(envJSON),
		int64(len(envJSON)),
		minio.PutObjectOptions{
			ContentType: "application/json",
		},
	)
	if err != nil {
		return nil, fmt.Errorf("envelope key kaydetme: %w", err)
	}

	return tenantKey, nil
}

// encryptAESGCM encrypts data using AES-256-GCM with the given key.
func encryptAESGCM(key, plaintext []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("aes cipher: %w", err)
	}

	aesGCM, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("gcm: %w", err)
	}

	nonce := make([]byte, aesGCM.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, fmt.Errorf("nonce: %w", err)
	}

	// Format: nonce (12 bayt) + ciphertext + tag
	ciphertext := aesGCM.Seal(nonce, nonce, plaintext, nil)
	return ciphertext, nil
}

// decryptAESGCM decrypts data using AES-256-GCM with the given key.
func decryptAESGCM(key, ciphertext []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("aes cipher: %w", err)
	}

	aesGCM, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("gcm: %w", err)
	}

	nonceSize := aesGCM.NonceSize()
	if len(ciphertext) < nonceSize {
		return nil, fmt.Errorf("şifreli veri çok kısa")
	}

	nonce, ciphertext := ciphertext[:nonceSize], ciphertext[nonceSize:]
	plaintext, err := aesGCM.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return nil, fmt.Errorf("aes gcm açma: %w", err)
	}

	return plaintext, nil
}

// DeriveMasterKeyFromEnv derives a master key from a configured env var.
// For development: uses SHA-256 hash of JWT_SECRET.
// For production: should use a separate, securely stored master key.
func DeriveMasterKeyFromEnv(jwtSecret string) string {
	hash := sha256.Sum256([]byte(jwtSecret + ":geolens-storage-master-key"))
	return hex.EncodeToString(hash[:])
}
