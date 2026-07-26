package agent

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestAgentNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestStartTrace_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/agents/traces", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.StartTrace(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestStartTrace_EmptyAgentName(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"agent_name": ""})
	req := httptest.NewRequest(http.MethodPost, "/v1/agents/traces", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.StartTrace(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestStartTrace_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"agent_name": "test-agent", "workflow_name": "test-workflow"})
	req := httptest.NewRequest(http.MethodPost, "/v1/agents/traces", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.StartTrace(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}
	var result map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&result)
	if result["status"] != "running" {
		t.Fatalf("got %v", result["status"])
	}
}

func TestGetTrace_NotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
		return &testutil.MockRow{Err: errors.New("not found")}
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/agents/traces/t-1", nil)
	w := httptest.NewRecorder()
	h.GetTrace(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestGetTrace_Success(t *testing.T) {
	now := time.Now()
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"agent-a", "wf-1", "running", 2, 1, 500, now, nil}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"s-1", "step-1", "agent-a", "input1", "output1", "completed", 200, "", now, now},
			}), nil
		},
	})
	req := httptest.NewRequest(http.MethodGet, "/v1/agents/traces/t-1", nil)
	w := httptest.NewRecorder()
	h.GetTrace(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestRecordStep_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/agents/traces/t-1/steps", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordStep(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecordStep_TraceNotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
		return &testutil.MockRow{Err: errors.New("not found")}
	}})
	body, _ := json.Marshal(map[string]string{"step_name": "test", "agent_name": "agent-a"})
	req := httptest.NewRequest(http.MethodPost, "/v1/agents/traces/t-1/steps", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordStep(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestCompleteTrace_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/agents/traces/t-1/complete", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CompleteTrace(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCompleteTrace_NotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
		return testutil.MockCommandResult{RowsAffectedVal: 0}, nil
	}})
	body, _ := json.Marshal(map[string]string{"status": "completed"})
	req := httptest.NewRequest(http.MethodPost, "/v1/agents/traces/t-1/complete", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CompleteTrace(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestCompleteTrace_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"status": "completed"})
	req := httptest.NewRequest(http.MethodPost, "/v1/agents/traces/t-1/complete", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CompleteTrace(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListTraces_Success(t *testing.T) {
	now := time.Now()
	h := NewHandler(&testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"t-1", "agent-a", "wf-1", "running", 3, 1, 500, now, nil},
			}), nil
		},
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{1}}
		},
	})
	req := httptest.NewRequest(http.MethodGet, "/v1/agents/traces", nil)
	w := httptest.NewRecorder()
	h.ListTraces(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}
