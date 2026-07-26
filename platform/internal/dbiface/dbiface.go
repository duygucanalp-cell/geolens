// Package dbiface provides shared database interface types used by handler packages.
// These interfaces decouple handler logic from specific database implementations,
// enabling mock-based unit testing and allowing pgx/pgconn types to satisfy them via structural typing.
package dbiface

import "context"

// RowScanner is implemented by types that can scan a single row into destinations.
type RowScanner interface {
	Scan(dest ...any) error
}

// CommandResult provides information about an executed SQL command.
type CommandResult interface {
	RowsAffected() int64
}

// RowsIter allows iterating over query result rows.
type RowsIter interface {
	Close()
	Err() error
	Next() bool
	Scan(dest ...any) error
}

// DB defines the database methods used by handler packages.
// Using custom interfaces (not pgx types) to enable mock-based unit testing.
type DB interface {
	Query(ctx context.Context, sql string, args ...any) (RowsIter, error)
	QueryRow(ctx context.Context, sql string, args ...any) RowScanner
	Exec(ctx context.Context, sql string, args ...any) (CommandResult, error)
	Begin(ctx context.Context) (Tx, error)
}

// Tx defines the transaction methods used by handler packages.
type Tx interface {
	Commit(ctx context.Context) error
	Rollback(ctx context.Context) error
	QueryRow(ctx context.Context, sql string, args ...any) RowScanner
	Exec(ctx context.Context, sql string, args ...any) (CommandResult, error)
}
