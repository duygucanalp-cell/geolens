package storage

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"time"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

// Client wraps minio.Client for S3-compatible storage operations.
type Client struct {
	mc     *minio.Client
	bucket string
}

// NewClient creates a new S3 storage client.
func NewClient(endpoint, accessKey, secretKey, bucket, region string, useSSL bool) (*Client, error) {
	mc, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: useSSL,
		Region: region,
	})
	if err != nil {
		return nil, fmt.Errorf("minio istemci oluşturma: %w", err)
	}

	// Bucket'ı kontrol et/yoksa oluştur
	ctx := context.Background()
	exists, err := mc.BucketExists(ctx, bucket)
	if err != nil {
		return nil, fmt.Errorf("bucket kontrol: %w", err)
	}
	if !exists {
		if err := mc.MakeBucket(ctx, bucket, minio.MakeBucketOptions{Region: region}); err != nil {
			return nil, fmt.Errorf("bucket oluşturma: %w", err)
		}
	}

	return &Client{mc: mc, bucket: bucket}, nil
}

// SaveRawResponse saves a raw engine response to S3 and returns the object key.
// Key pattern: raw/{tenant}/{workspace}/{engine}/{date}/{uuid}.json
func (c *Client) SaveRawResponse(ctx context.Context, tenantID, workspaceID, engineName string, data []byte) (string, error) {
	now := time.Now()
	key := fmt.Sprintf("raw/%s/%s/%s/%s/%s.json",
		tenantID,
		workspaceID,
		engineName,
		now.Format("2006/01/02"),
		now.Format("150405")+"-"+fmt.Sprintf("%x", now.UnixNano())[:8],
	)

	_, err := c.mc.PutObject(ctx, c.bucket, key,
		bytes.NewReader(data),
		int64(len(data)),
		minio.PutObjectOptions{
			ContentType: "application/json",
		},
	)
	if err != nil {
		return "", fmt.Errorf("s3 raw yanıt kaydetme: %w", err)
	}

	return key, nil
}

// GetObject retrieves an object from S3 by key.
func (c *Client) GetObject(ctx context.Context, key string) ([]byte, error) {
	obj, err := c.mc.GetObject(ctx, c.bucket, key, minio.GetObjectOptions{})
	if err != nil {
		return nil, fmt.Errorf("s3 nesne okuma: %w", err)
	}
	defer obj.Close()

	data, err := io.ReadAll(obj)
	if err != nil {
		return nil, fmt.Errorf("s3 nesne veri okuma: %w", err)
	}

	return data, nil
}

// PresignedURL generates a time-limited presigned URL for an object.
func (c *Client) PresignedURL(ctx context.Context, key string, expiry time.Duration) (string, error) {
	url, err := c.mc.PresignedGetObject(ctx, c.bucket, key, expiry, nil)
	if err != nil {
		return "", fmt.Errorf("s3 presigned url: %w", err)
	}
	return url.String(), nil
}
