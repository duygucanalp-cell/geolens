// Package testutil provides testutil functionality.
//
//nolint:cyclop
package testutil

import (
	"context"
	"io"
	"time"

	"github.com/geolens/platform/internal/dbiface"
)

// MockCommandResult implements dbiface.CommandResult interface for testing.
type MockCommandResult struct {
	RowsAffectedVal int64
}

func (m MockCommandResult) RowsAffected() int64 { return m.RowsAffectedVal }

// MockRow implements dbiface.RowScanner interface for testing.
type MockRow struct {
	Values []any
	Err    error
}

func (m *MockRow) Scan(dest ...any) error {
	if m.Err != nil {
		return m.Err
	}
	for i, v := range m.Values {
		if i >= len(dest) {
			break
		}
		switch d := dest[i].(type) {
		case *string:
			if s, ok := v.(string); ok {
				*d = s
			}
		case *int:
			if n, ok := v.(int); ok {
				*d = n
			}
		case *float64:
			if f, ok := v.(float64); ok {
				*d = f
			}
		case *bool:
			if b, ok := v.(bool); ok {
				*d = b
			}
		case *int64:
			if n, ok := v.(int64); ok {
				*d = n
			}
		case *time.Time:
			if t, ok := v.(time.Time); ok {
				*d = t
			} else if s, ok := v.(string); ok {
				if parsed, err := time.Parse(time.RFC3339, s); err == nil {
					*d = parsed
				}
			}
		case **time.Time:
			if v == nil {
				*d = nil
			} else if t, ok := v.(time.Time); ok {
				*d = &t
			} else if s, ok := v.(string); ok {
				if parsed, err := time.Parse(time.RFC3339, s); err == nil {
					*d = &parsed
				}
			}
		case **string:
			if v == nil {
				*d = nil
			} else if s, ok := v.(string); ok {
				*d = &s
			} else if sp, ok := v.(*string); ok {
				*d = sp
			}
		case *[]byte:
			if b, ok := v.([]byte); ok {
				*d = b
			} else if s, ok := v.(string); ok {
				*d = []byte(s)
			}
		case *any:
			*d = v
		}
	}
	return nil
}

// MockRows implements dbiface.RowsIter interface for testing.
type MockRows struct {
	Data   [][]any
	Cursor int
	ErrVal error
}

// NewMockRows creates a MockRows with cursor initialized to -1.
func NewMockRows(data [][]any) *MockRows {
	return &MockRows{Data: data, Cursor: -1}
}

func (m *MockRows) Close() {}

func (m *MockRows) Err() error { return m.ErrVal }

func (m *MockRows) Next() bool {
	m.Cursor++
	return m.Cursor < len(m.Data)
}

func (m *MockRows) Scan(dest ...any) error {
	if m.Cursor < 0 || m.Cursor >= len(m.Data) {
		return io.EOF
	}
	row := m.Data[m.Cursor]
	for i, v := range row {
		if i >= len(dest) {
			break
		}
		switch d := dest[i].(type) {
		case *string:
			if s, ok := v.(string); ok {
				*d = s
			}
		case *int:
			if n, ok := v.(int); ok {
				*d = n
			}
		case *float64:
			if f, ok := v.(float64); ok {
				*d = f
			}
		case *bool:
			if b, ok := v.(bool); ok {
				*d = b
			}
		case *int64:
			if n, ok := v.(int64); ok {
				*d = n
			}
		case *time.Time:
			if t, ok := v.(time.Time); ok {
				*d = t
			} else if s, ok := v.(string); ok {
				if parsed, err := time.Parse(time.RFC3339, s); err == nil {
					*d = parsed
				}
			}
		case **time.Time:
			if v == nil {
				*d = nil
			} else if t, ok := v.(time.Time); ok {
				*d = &t
			} else if s, ok := v.(string); ok {
				if parsed, err := time.Parse(time.RFC3339, s); err == nil {
					*d = &parsed
				}
			}
		case **string:
			if v == nil {
				*d = nil
			} else if s, ok := v.(string); ok {
				*d = &s
			} else if sp, ok := v.(*string); ok {
				*d = sp
			}
		case *[]byte:
			if b, ok := v.([]byte); ok {
				*d = b
			} else if s, ok := v.(string); ok {
				*d = []byte(s)
			}
		case *any:
			*d = v
		}
	}
	return nil
}

// MockTx implements dbiface.Tx interface for testing.
type MockTx struct {
	CommitFunc   func(ctx context.Context) error
	RollbackFunc func(ctx context.Context) error
	QueryRowFunc func(ctx context.Context, sql string, args ...any) dbiface.RowScanner
	ExecFunc     func(ctx context.Context, sql string, args ...any) (dbiface.CommandResult, error)
}

func (m *MockTx) Commit(ctx context.Context) error {
	if m.CommitFunc != nil {
		return m.CommitFunc(ctx)
	}
	return nil
}

func (m *MockTx) Rollback(ctx context.Context) error {
	if m.RollbackFunc != nil {
		return m.RollbackFunc(ctx)
	}
	return nil
}

func (m *MockTx) QueryRow(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
	if m.QueryRowFunc != nil {
		return m.QueryRowFunc(ctx, sql, args...)
	}
	return &MockRow{}
}

func (m *MockTx) Exec(ctx context.Context, sql string, args ...any) (dbiface.CommandResult, error) {
	if m.ExecFunc != nil {
		return m.ExecFunc(ctx, sql, args...)
	}
	return MockCommandResult{RowsAffectedVal: 1}, nil
}

// MockPool implements dbiface.DB interface for testing.
// Function fields can be set per-test to control behavior;
// nil fields return default no-op responses.
type MockPool struct {
	QueryFunc    func(ctx context.Context, sql string, args ...any) (dbiface.RowsIter, error)
	QueryRowFunc func(ctx context.Context, sql string, args ...any) dbiface.RowScanner
	ExecFunc     func(ctx context.Context, sql string, args ...any) (dbiface.CommandResult, error)
	BeginFunc    func(ctx context.Context) (dbiface.Tx, error)
}

func (m *MockPool) Query(ctx context.Context, sql string, args ...any) (dbiface.RowsIter, error) {
	if m.QueryFunc != nil {
		return m.QueryFunc(ctx, sql, args...)
	}
	return NewMockRows(nil), nil
}

func (m *MockPool) QueryRow(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
	if m.QueryRowFunc != nil {
		return m.QueryRowFunc(ctx, sql, args...)
	}
	return &MockRow{}
}

func (m *MockPool) Exec(ctx context.Context, sql string, args ...any) (dbiface.CommandResult, error) {
	if m.ExecFunc != nil {
		return m.ExecFunc(ctx, sql, args...)
	}
	return MockCommandResult{RowsAffectedVal: 1}, nil
}

func (m *MockPool) Begin(ctx context.Context) (dbiface.Tx, error) {
	if m.BeginFunc != nil {
		return m.BeginFunc(ctx)
	}
	return &MockTx{}, nil
}

// StrPtr is a helper to create a *string literal.
func StrPtr(s string) *string { return &s }
