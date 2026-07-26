package registry

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

// ---- Constructor Tests ----

func TestNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
	if h.pool != nil {
		t.Fatal("expected nil pool")
	}
}

func TestWithIndexer(t *testing.T) {
	h := NewHandler(nil)
	if _, ok := h.indexer.(noopIndexer); !ok {
		t.Fatal("expected default noopIndexer")
	}
}

// ---- CREATE Tests ----

func TestCreate_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	req := httptest.NewRequest(http.MethodPost, "/v1/registry/entities", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Create(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestCreate_InvalidEntityType(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	tests := []struct {
		name       string
		entityType string
	}{
		{name: "unsupported type", entityType: "llm"},
		{name: "empty type", entityType: ""},
		{name: "random string", entityType: "foobar"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(map[string]string{
				"entity_type": tt.entityType,
				"name":        "test-model",
			})
			req := httptest.NewRequest(http.MethodPost, "/v1/registry/entities", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()

			h.Create(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusBadRequest {
				t.Fatalf("expected 400 for entity_type=%q, got %d", tt.entityType, resp.StatusCode)
			}
		})
	}
}

func TestCreate_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, args ...any) dbiface.RowScanner {
			entityType := "model"
			if len(args) > 1 {
				if et, ok := args[1].(string); ok {
					entityType = et
				}
			}
			return &testutil.MockRow{
				Values: []any{
					"ent-001",                  // id
					"tenant-1",                 // tenant_id
					entityType,                 // entity_type
					"MyModel",                  // name
					"Test model description",   // description
					"1.0.0",                    // version
					"openai",                   // provider
					"development",              // lifecycle_state
					"medium",                   // risk_class
					"user-1",                   // owner
					"https://docs.example.com", // documentation_url
					(*string)(nil),             // deployed_at (nil)
					"2026-07-25T10:00:00Z",     // created_at
					"2026-07-25T10:00:00Z",     // updated_at
				},
			}
		},
	}
	h := NewHandler(pool)

	body, _ := json.Marshal(map[string]string{
		"entity_type": "model",
		"name":        "MyModel",
		"description": "Test model description",
		"version":     "1.0.0",
		"provider":    "openai",
		"owner":       "user-1",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/registry/entities", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Create(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}

	var entity Entity
	if err := json.NewDecoder(resp.Body).Decode(&entity); err != nil {
		t.Fatalf("decode error: %v", err)
	}
	if entity.ID != "ent-001" {
		t.Fatalf("expected entity ID 'ent-001', got %q", entity.ID)
	}
	if entity.Name != "MyModel" {
		t.Fatalf("expected name 'MyModel', got %q", entity.Name)
	}
	if entity.EntityType != "model" {
		t.Fatalf("expected entity_type 'model', got %q", entity.EntityType)
	}
}

func TestCreate_DBError(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("db error")}
		},
	}
	h := NewHandler(pool)

	body, _ := json.Marshal(map[string]string{
		"entity_type": "model",
		"name":        "MyModel",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/registry/entities", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Create(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", resp.StatusCode)
	}
}

func TestCreate_DefaultValues(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, args ...any) dbiface.RowScanner {
			// Verify defaults are passed as SQL args
			if len(args) > 8 {
				if lifecycle, ok := args[6].(string); ok && lifecycle != "development" {
					t.Errorf("expected default lifecycle_state 'development', got %q", lifecycle)
				}
				if risk, ok := args[7].(string); ok && risk != "medium" {
					t.Errorf("expected default risk_class 'medium', got %q", risk)
				}
			}
			if len(args) > 6 {
				if version, ok := args[4].(string); ok && version != "1.0.0" {
					t.Errorf("expected default version '1.0.0', got %q", version)
				}
			}
			return &testutil.MockRow{
				Values: []any{
					"ent-002", "tenant-1", "agent", "MyAgent", "", "1.0.0",
					"", "development", "medium", "", "", nil, "now", "now",
				},
			}
		},
	}
	h := NewHandler(pool)

	body, _ := json.Marshal(map[string]string{
		"entity_type": "agent",
		"name":        "MyAgent",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/registry/entities", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Create(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}
}

// ---- READ (List) Tests ----

func TestList_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{
					"ent-001", "tenant-1", "model", "Model A", "Description A",
					"1.0.0", "openai", "production", "medium", "user-1",
					"https://docs.a.com", testutil.StrPtr("2026-07-01T00:00:00Z"),
					"2026-07-01T00:00:00Z", "2026-07-25T00:00:00Z",
				},
				{
					"ent-002", "tenant-1", "agent", "Agent B", "Description B",
					"2.0.0", "anthropic", "development", "high", "user-2",
					"https://docs.b.com", (*string)(nil),
					"2026-07-20T00:00:00Z", "2026-07-25T00:00:00Z",
				},
			}), nil
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodGet, "/v1/registry/entities", nil)
	w := httptest.NewRecorder()

	h.List(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	var body map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		t.Fatalf("decode error: %v", err)
	}
	entities, ok := body["entities"].([]interface{})
	if !ok {
		t.Fatal("expected entities array")
	}
	if len(entities) != 2 {
		t.Fatalf("expected 2 entities, got %d", len(entities))
	}
}

func TestList_Empty(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{}), nil
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodGet, "/v1/registry/entities", nil)
	w := httptest.NewRecorder()

	h.List(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	ents := body["entities"]
	if ents == nil {
		return // nil entities is acceptable for empty result
	}
	entities, ok := ents.([]interface{})
	if !ok {
		t.Fatal("expected entities array")
	}
	if len(entities) != 0 {
		t.Fatalf("expected empty list, got %d items", len(entities))
	}
}

func TestList_QueryError(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return nil, errors.New("connection error")
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodGet, "/v1/registry/entities", nil)
	w := httptest.NewRecorder()

	h.List(w, req)

	resp := w.Result()
	// On query error, handler returns 200 with empty entities (graceful degradation)
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", resp.StatusCode)
	}

	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	ents := body["entities"]
	if ents == nil {
		return // nil entities on error is acceptable
	}
	entities, ok := ents.([]interface{})
	if !ok {
		t.Fatal("expected entities array")
	}
	if len(entities) != 0 {
		t.Fatalf("expected empty entities on error, got %d", len(entities))
	}
}

func TestList_WithFilters(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, sql string, args ...any) (dbiface.RowsIter, error) {
			if len(args) < 2 {
				t.Error("expected at least 2 args (tenant_id + filter)")
			}
			if len(args) >= 2 {
				if ft, ok := args[1].(string); !ok || ft != "model" {
					t.Errorf("expected filter 'model', got %v", args[1])
				}
			}
			return testutil.NewMockRows([][]any{
				{
					"ent-001", "tenant-1", "model", "Model A", "",
					"1.0.0", "openai", "production", "medium", "user-1",
					"", nil, "now", "now",
				},
			}), nil
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodGet, "/v1/registry/entities?entity_type=model", nil)
	w := httptest.NewRecorder()

	h.List(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
}

// ---- READ (Get) Tests ----

func TestGet_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, args ...any) dbiface.RowScanner {
			return &testutil.MockRow{
				Values: []any{
					"ent-001", "tenant-1", "model", "Model A", "Description",
					"1.0.0", "openai", "production", "medium", "user-1",
					"https://docs.a.com", nil, "2026-07-01T00:00:00Z", "2026-07-25T00:00:00Z",
				},
			}
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodGet, "/v1/registry/entities/ent-001", nil)
	w := httptest.NewRecorder()

	h.Get(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	var entity Entity
	json.NewDecoder(resp.Body).Decode(&entity)
	if entity.ID != "ent-001" {
		t.Fatalf("expected ID 'ent-001', got %q", entity.ID)
	}
	if entity.Name != "Model A" {
		t.Fatalf("expected name 'Model A', got %q", entity.Name)
	}
}

func TestGet_NotFound(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("not found")}
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodGet, "/v1/registry/entities/nonexistent", nil)
	w := httptest.NewRecorder()

	h.Get(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", resp.StatusCode)
	}
}

// ---- UPDATE Tests ----

func TestUpdate_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	req := httptest.NewRequest(http.MethodPut, "/v1/registry/entities/123", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Update(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestUpdate_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, args ...any) dbiface.RowScanner {
			return &testutil.MockRow{
				Values: []any{
					"ent-001", "tenant-1", "model", "Updated Name", "Updated desc",
					"2.0.0", "openai", "production", "high", "user-1",
					"https://docs.example.com", nil, "2026-07-01T00:00:00Z", "2026-07-25T12:00:00Z",
				},
			}
		},
	}
	h := NewHandler(pool)

	body, _ := json.Marshal(map[string]string{
		"name":            "Updated Name",
		"description":     "Updated desc",
		"version":         "2.0.0",
		"lifecycle_state": "production",
		"risk_class":      "high",
	})
	req := httptest.NewRequest(http.MethodPut, "/v1/registry/entities/ent-001", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Update(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	var entity Entity
	json.NewDecoder(resp.Body).Decode(&entity)
	if entity.Name != "Updated Name" {
		t.Fatalf("expected 'Updated Name', got %q", entity.Name)
	}
	if entity.Version != "2.0.0" {
		t.Fatalf("expected version '2.0.0', got %q", entity.Version)
	}
}

func TestUpdate_NotFound(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("not found")}
		},
	}
	h := NewHandler(pool)

	body, _ := json.Marshal(map[string]string{"name": "New Name"})
	req := httptest.NewRequest(http.MethodPut, "/v1/registry/entities/nonexistent", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Update(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", resp.StatusCode)
	}
}

// ---- DELETE Tests ----

func TestDelete_Success(t *testing.T) {
	pool := &testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodDelete, "/v1/registry/entities/ent-001", nil)
	w := httptest.NewRecorder()

	h.Delete(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}

	var body map[string]string
	json.NewDecoder(resp.Body).Decode(&body)
	if body["status"] != "silindi" {
		t.Fatalf("expected 'silindi', got %q", body["status"])
	}
}

func TestDelete_NotFound(t *testing.T) {
	pool := &testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 0}, nil
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodDelete, "/v1/registry/entities/nonexistent", nil)
	w := httptest.NewRecorder()

	h.Delete(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", resp.StatusCode)
	}
}

func TestDelete_DBError(t *testing.T) {
	pool := &testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return nil, errors.New("db error")
		},
	}
	h := NewHandler(pool)

	req := httptest.NewRequest(http.MethodDelete, "/v1/registry/entities/ent-001", nil)
	w := httptest.NewRecorder()

	h.Delete(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", resp.StatusCode)
	}
}

// ---- RISK ASSESSMENT Tests ----

func TestAssessRisk_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	req := httptest.NewRequest(http.MethodPost, "/v1/registry/entities/123/assess", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.AssessRisk(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestAssessRisk_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"assessment-001"}}
		},
	}
	h := NewHandler(pool)

	body, _ := json.Marshal(map[string]interface{}{
		"risk_class": "high",
		"score":      85.5,
		"summary":    "High risk due to PII processing",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/registry/entities/ent-001/assess", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.AssessRisk(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}

	var result map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&result)
	if result["id"] != "assessment-001" {
		t.Fatalf("expected assessment ID 'assessment-001', got %v", result["id"])
	}
	if result["status"] != "değerlendirildi" {
		t.Fatalf("expected status 'değerlendirildi', got %v", result["status"])
	}
}

func TestAssessRisk_DBError(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("db error")}
		},
	}
	h := NewHandler(pool)

	body, _ := json.Marshal(map[string]interface{}{
		"risk_class": "low",
		"score":      10.0,
		"summary":    "Low risk",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/registry/entities/ent-001/assess", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.AssessRisk(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", resp.StatusCode)
	}
}

// ---- Indexer Tests ----

func TestNoopIndexer(t *testing.T) {
	e := noopIndexer{}

	if err := e.IndexEntity(context.TODO(), Entity{}); err != nil {
		t.Fatalf("noop IndexEntity should not error: %v", err)
	}
	if err := e.DeleteEntity(context.TODO(), "test-id"); err != nil {
		t.Fatalf("noop DeleteEntity should not error: %v", err)
	}
}

func TestESIndexer_NilClient(t *testing.T) {
	e := &esIndexer{client: nil}

	if err := e.IndexEntity(context.TODO(), Entity{}); err != nil {
		t.Fatalf("esIndexer with nil client: %v", err)
	}
	if err := e.DeleteEntity(context.TODO(), "test-id"); err != nil {
		t.Fatalf("esIndexer with nil client: %v", err)
	}
}

// ---- Helper ----
