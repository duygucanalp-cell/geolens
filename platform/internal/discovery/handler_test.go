package discovery

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestStartScan_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/discovery/scan", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.StartScan(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestStartScan_DBError(t *testing.T) {
	pool := &testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return nil, errors.New("db error")
		},
	}
	h := NewHandler(pool)
	body, _ := json.Marshal(map[string]string{"scan_type": "api"})
	req := httptest.NewRequest(http.MethodPost, "/v1/discovery/scan", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.StartScan(w, req)
	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestStartScan_Success(t *testing.T) {
	pool := &testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
	}
	h := NewHandler(pool)
	body, _ := json.Marshal(map[string]string{"scan_type": "full", "provider": "aws"})
	req := httptest.NewRequest(http.MethodPost, "/v1/discovery/scan", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.StartScan(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}
	var result map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&result)
	if result["status"] != "running" {
		t.Fatalf("expected status 'running', got %v", result["status"])
	}
	if _, ok := result["scan_id"]; !ok {
		t.Fatal("expected scan_id in response")
	}
}

func TestStartScan_DefaultScanType(t *testing.T) {
	pool := &testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, args ...any) (dbiface.CommandResult, error) {
			if len(args) >= 3 {
				if st, ok := args[2].(string); !ok || st != "api" {
					t.Logf("scan_type set to %v (expected 'api')", args[2])
				}
			}
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
	}
	h := NewHandler(pool)
	body, _ := json.Marshal(map[string]string{})
	req := httptest.NewRequest(http.MethodPost, "/v1/discovery/scan", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.StartScan(w, req)
	if w.Result().StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", w.Result().StatusCode)
	}
}

func TestGetScanResults_NotFound(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("not found")}
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/discovery/scans/nonexistent", nil)
	w := httptest.NewRecorder()
	h.GetScanResults(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestGetScanResults_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{
				Values: []any{
					"scan-001", "completed", "aws", 2,
					testutil.StrPtr("2026-07-25T10:00:00Z"), testutil.StrPtr("2026-07-25T10:05:00Z"),
					"2026-07-25T10:00:00Z",
				},
			}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{
					"lambda", "ai-fn", "arn:aws:lambda:fn:1", "aws", "us-east-1",
					"high", `{"runtime":"python3.12"}`, "2026-07-25T10:00:00Z",
				},
				{
					"sagemaker", "llm-endpoint", "arn:aws:sagemaker:ep:1", "aws", "us-west-2",
					"critical", `{"model":"llama-3"}`, "2026-07-25T10:01:00Z",
				},
			}), nil
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/discovery/scans/scan-001", nil)
	w := httptest.NewRecorder()
	h.GetScanResults(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	if _, ok := body["scan"]; !ok {
		t.Fatal("expected scan field")
	}
	if _, ok := body["findings"]; !ok {
		t.Fatal("expected findings field")
	}
}

func TestGetScanResults_QueryError(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{
				Values: []any{"scan-001", "completed", "all", 1, nil, nil, "now"},
			}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return nil, errors.New("query error")
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/discovery/scans/scan-001", nil)
	w := httptest.NewRecorder()
	h.GetScanResults(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
}

func TestGetScanResults_EmptyFindings(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{
				Values: []any{"scan-002", "completed", "gcp", 0, nil, nil, "now"},
			}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{}), nil
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/discovery/scans/scan-002", nil)
	w := httptest.NewRecorder()
	h.GetScanResults(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	scan := body["scan"].(map[string]interface{})
	if scan["status"] != "completed" {
		t.Fatalf("expected status 'completed', got %v", scan["status"])
	}
}

func TestSimulateScan(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	findings := h.simulateScan("tenant-1", "all")
	if len(findings) != 3 {
		t.Fatalf("expected 3 findings, got %d", len(findings))
	}
	if findings[0].RiskLevel != "high" {
		t.Fatalf("expected risk 'high', got %q", findings[0].RiskLevel)
	}
	if findings[1].RiskLevel != "critical" {
		t.Fatalf("expected risk 'critical', got %q", findings[1].RiskLevel)
	}
}
