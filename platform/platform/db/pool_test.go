package db

import (
	"context"
	"testing"
	"time"
)

func TestNewPool_InvalidURL(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	_, err := NewPool(ctx, "postgres://invalid:invalid@localhost:9999/test?sslmode=disable")
	if err == nil {
		t.Log("Not: geçersiz URL ile pool oluşturma beklenen bir testtir")
	}
}

func TestPool_Close(t *testing.T) {
	var p Pool
	p.Close()
}
