package dbiface

import (
	"context"

	"github.com/jackc/pgx/v5"

	"github.com/geolens/platform/platform/db"
)

// poolAdapter wraps a *db.Pool to implement the DB interface.
type poolAdapter struct {
	Pool *db.Pool
}

func (a poolAdapter) Query(ctx context.Context, sql string, args ...any) (RowsIter, error) {
	return a.Pool.Query(ctx, sql, args...)
}

func (a poolAdapter) QueryRow(ctx context.Context, sql string, args ...any) RowScanner {
	return a.Pool.QueryRow(ctx, sql, args...)
}

func (a poolAdapter) Exec(ctx context.Context, sql string, args ...any) (CommandResult, error) {
	return a.Pool.Exec(ctx, sql, args...)
}

func (a poolAdapter) Begin(ctx context.Context) (Tx, error) {
	tx, err := a.Pool.Begin(ctx)
	if err != nil {
		return nil, err
	}
	return txAdapter{tx: tx}, nil
}

// NewAdapter wraps a *db.Pool into a DB interface.
func NewAdapter(pool *db.Pool) DB {
	return poolAdapter{Pool: pool}
}

// RawPool returns the underlying *db.Pool from a DB adapter.
// Returns nil if the given DB is not backed by a poolAdapter.
func RawPool(d DB) *db.Pool {
	if a, ok := d.(poolAdapter); ok {
		return a.Pool
	}
	return nil
}

// txAdapter wraps a pgx.Tx to implement the Tx interface.
type txAdapter struct {
	tx pgx.Tx
}

func (a txAdapter) Commit(ctx context.Context) error {
	return a.tx.Commit(ctx)
}

func (a txAdapter) Rollback(ctx context.Context) error {
	return a.tx.Rollback(ctx)
}

func (a txAdapter) QueryRow(ctx context.Context, sql string, args ...any) RowScanner {
	return a.tx.QueryRow(ctx, sql, args...)
}

func (a txAdapter) Exec(ctx context.Context, sql string, args ...any) (CommandResult, error) {
	tag, err := a.tx.Exec(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	return tag, nil
}
