package errors

import (
	"errors"
	"fmt"
	"net/http"
)

// Domain error types
var (
	ErrNotFound      = NewError("not_found", http.StatusNotFound)
	ErrValidation    = NewError("validation_error", http.StatusBadRequest)
	ErrConflict      = NewError("conflict", http.StatusConflict)
	ErrUnauthorized  = NewError("unauthorized", http.StatusUnauthorized)
	ErrForbidden     = NewError("forbidden", http.StatusForbidden)
	ErrRateLimited   = NewError("rate_limited", http.StatusTooManyRequests)
	ErrInternal      = NewError("internal_error", http.StatusInternalServerError)
	ErrQuotaExceeded = NewError("quota_exceeded", http.StatusTooManyRequests)
)

// Error represents a structured domain error with an HTTP status code.
type Error struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	HTTPSC  int    `json:"-"`
	Err     error  `json:"-"`
}

// NewError creates a new Error type with the given code and status.
func NewError(code string, httpStatus int) *Error {
	return &Error{Code: code, HTTPSC: httpStatus}
}

// New returns a new Error with the given message.
func (e *Error) New(msg string) *Error {
	return &Error{Code: e.Code, Message: msg, HTTPSC: e.HTTPSC}
}

// Error implements the error interface.
func (e *Error) Error() string {
	if e.Err != nil {
		return fmt.Sprintf("%s: %s (%v)", e.Code, e.Message, e.Err)
	}
	if e.Message != "" {
		return fmt.Sprintf("%s: %s", e.Code, e.Message)
	}
	return e.Code
}

// Unwrap returns the wrapped error.
func (e *Error) Unwrap() error {
	return e.Err
}

// Is checks if the error matches a target.
func (e *Error) Is(target error) bool {
	t, ok := target.(*Error)
	if !ok {
		return false
	}
	return e.Code == t.Code
}

// Convenience constructors

func NotFound(msg string) *Error {
	return ErrNotFound.New(msg)
}

func NotFoundE(msg string, err error) *Error {
	return &Error{Code: ErrNotFound.Code, Message: msg, HTTPSC: ErrNotFound.HTTPSC, Err: err}
}

func Validation(msg string) *Error {
	return ErrValidation.New(msg)
}

func Conflict(msg string) *Error {
	return ErrConflict.New(msg)
}

func Unauthorized(msg string) *Error {
	return ErrUnauthorized.New(msg)
}

func Forbidden(msg string) *Error {
	return ErrForbidden.New(msg)
}

func Internal(msg string, err error) *Error {
	return &Error{Code: ErrInternal.Code, Message: msg, HTTPSC: ErrInternal.HTTPSC, Err: err}
}

func RateLimited(msg string) *Error {
	return ErrRateLimited.New(msg)
}

func QuotaExceeded(msg string) *Error {
	return ErrQuotaExceeded.New(msg)
}

// StatusCode returns the HTTP status code for an error.
func StatusCode(err error) int {
	var domainErr *Error
	if errors.As(err, &domainErr) {
		return domainErr.HTTPSC
	}
	return http.StatusInternalServerError
}

// Code returns the error code string.
func Code(err error) string {
	var domainErr *Error
	if errors.As(err, &domainErr) {
		return domainErr.Code
	}
	return "unknown_error"
}
