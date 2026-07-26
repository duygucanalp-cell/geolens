//go:build integration

package measure

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/platform/db"
)

func setupTestDB(t *testing.T) (*db.Pool, func()) {
	t.Helper()

	ctx, cancel := context.WithTimeout(context.Background(), 120*time.Second)
	defer cancel()

	req := testcontainers.ContainerRequest{
		Image:        "postgres:16-alpine",
		ExposedPorts: []string{"5432/tcp"},
		Env: map[string]string{
			"POSTGRES_USER":     "geolens",
			"POSTGRES_PASSWORD": "geolens",
			"POSTGRES_DB":       "geolens_test",
		},
		WaitingFor: wait.ForLog("database system is ready to accept connections").
			WithOccurrence(2).
			WithStartupTimeout(60 * time.Second),
	}

	pgContainer, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	require.NoError(t, err)

	host, err := pgContainer.Host(ctx)
	require.NoError(t, err)
	port, err := pgContainer.MappedPort(ctx, "5432")
	require.NoError(t, err)

	dsn := fmt.Sprintf("postgres://geolens:geolens@%s:%s/geolens_test?sslmode=disable", host, port.Port())

	pool, err := db.NewPool(context.Background(), dsn)
	require.NoError(t, err)

	// Run all migrations
	migrationsDir := filepath.Join("..", "..", "migrations")
	entries, err := os.ReadDir(migrationsDir)
	require.NoError(t, err)

	migFiles := make([]string, 0, len(entries))
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			migFiles = append(migFiles, e.Name())
		}
	}
	sort.Strings(migFiles)

	for _, m := range migFiles {
		sqlBytes, err := os.ReadFile(filepath.Join(migrationsDir, m))
		require.NoError(t, err)
		_, err = pool.Exec(context.Background(), string(sqlBytes))
		require.NoError(t, err, "migration %s failed", m)
	}

	cleanup := func() {
		pool.Close()
		testcontainers.CleanupContainer(t, pgContainer)
	}

	return pool, cleanup
}

func seedTestData(t *testing.T, pool *db.Pool) (tenantID, workspaceID, brandID string) {
	t.Helper()

	tenantID = "T01"
	workspaceID = "WS01"
	brandID = "B01"

	_, err := pool.Exec(context.Background(), `
		INSERT INTO identity.tenants (id, name, slug, tier) VALUES ($1, 'Test Tenant', 'test-tenant', 'free')
		ON CONFLICT DO NOTHING
	`, tenantID)
	require.NoError(t, err)

	_, err = pool.Exec(context.Background(), `
		INSERT INTO config.workspaces (id, tenant_id, name, slug) VALUES ($1, $2, 'Test Workspace', 'test-ws')
		ON CONFLICT DO NOTHING
	`, workspaceID, tenantID)
	require.NoError(t, err)

	_, err = pool.Exec(context.Background(), `
		INSERT INTO config.brands (id, workspace_id, tenant_id, name, website_url) VALUES ($1, $2, $3, 'Acme', 'https://acme.example.com')
		ON CONFLICT DO NOTHING
	`, brandID, workspaceID, tenantID)
	require.NoError(t, err)

	// Set tenant context for RLS
	_, err = pool.Exec(context.Background(), "SELECT set_config('app.tenant_id', $1, true)", tenantID)
	require.NoError(t, err)

	return
}

type mockEngine struct {
	name string
}

func (m *mockEngine) Execute(_ context.Context, prompt string) (*engine.RawResponse, error) {
	return &engine.RawResponse{
		Content:       fmt.Sprintf("Mock response for prompt: %s", prompt),
		EngineName:    m.name,
		Tier:          engine.TierStandard,
		FidelityLabel: "Kademe 2",
		Citations: []engine.Citation{
			{URL: "https://example.com/article1", Title: "Article 1", Snippet: "..."},
			{URL: "https://test.org/report", Title: "Report", Snippet: "..."},
			{URL: "https://sample.net/research", Title: "Research", Snippet: "..."},
		},
	}, nil
}

func (m *mockEngine) Name() string { return m.name }

type mockRegistry struct{}

func (r *mockRegistry) List() []string                                     { return []string{"mock-engine"} }
func (r *mockRegistry) Get(name string) engine.Adapter                     { return &mockEngine{name: name} }
func (r *mockRegistry) Register(name string, adapter engine.Adapter) error { return nil }
func (r *mockRegistry) Unregister(name string)                             {}

func TestServiceIntegration_MeasureAndScore(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test skipped in short mode")
	}

	pool, cleanup := setupTestDB(t)
	defer cleanup()

	tenantID, workspaceID, brandID := seedTestData(t, pool)

	svc := NewService(pool, &mockRegistry{}, nil)

	// Run Measure
	req := MeasurementRequest{
		BrandName:   "Acme",
		PromptText:  "Acme markası hakkında ne biliyorsun?",
		EngineName:  "mock-engine",
		PanelID:     "",
		WorkspaceID: workspaceID,
		TenantID:    tenantID,
	}
	result, err := svc.Measure(context.Background(), req)
	require.NoError(t, err)
	require.NotNil(t, result)
	require.Len(t, result.RawResponses, 3)
	require.Len(t, result.Citations, 9)
	require.Equal(t, brandID, result.BrandName, "BrandName fallback to brandID")

	// Run CalculateScore
	score, err := svc.CalculateScore(context.Background(), "", []MeasurementResult{*result}, ComponentWeights{})
	require.NoError(t, err)
	require.NotNil(t, score)
	require.Greater(t, score.Value, 0.0)
	require.LessOrEqual(t, score.Value, 100.0)
	require.NotEmpty(t, score.ID)
	require.NotEmpty(t, score.CalculationRunID)

	// Verify score persisted
	saved, err := svc.GetScoreByID(context.Background(), score.ID)
	require.NoError(t, err)
	require.NotNil(t, saved)
	require.Equal(t, score.Value, saved.Value)
}

func TestServiceIntegration_CalculateScore_EmptyResults(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test skipped in short mode")
	}

	pool, cleanup := setupTestDB(t)
	defer cleanup()

	svc := NewService(pool, &mockRegistry{}, nil)

	_, err := svc.CalculateScore(context.Background(), "", nil, ComponentWeights{})
	require.Error(t, err)
	require.Contains(t, err.Error(), "veri yok")
}

func TestServiceIntegration_GetScoreByID_NotFound(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test skipped in short mode")
	}

	pool, cleanup := setupTestDB(t)
	defer cleanup()

	svc := NewService(pool, &mockRegistry{}, nil)

	_, err := svc.GetScoreByID(context.Background(), "nonexistent-id")
	require.Error(t, err)
}

func TestServiceIntegration_PoolClosed(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test skipped in short mode")
	}

	pool, cleanup := setupTestDB(t)
	defer cleanup()

	pool.Close()

	svc := NewService(pool, &mockRegistry{}, nil)

	// Both operations should fail gracefully
	req := MeasurementRequest{
		BrandName:   "Acme",
		PromptText:  "test",
		WorkspaceID: "ws",
		TenantID:    "t",
	}
	_, err := svc.Measure(context.Background(), req)
	require.Error(t, err)
}

// Ensure pgxpool is used (import side-effect)
var _ = &pgxpool.Pool{}
