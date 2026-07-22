module github.com/geolens/platform

go 1.23.0

require (
	github.com/go-chi/chi/v5 v5.1.0
	github.com/jackc/pgx/v5 v5.7.1
	github.com/redis/go-redis/v9 v9.7.0
	github.com/rs/zerolog v1.33.0
	github.com/google/uuid v1.6.0
	github.com/joho/godotenv v1.5.1
	github.com/testcontainers/testcontainers-go v0.34.0
	github.com/stretchr/testify v1.9.0
	github.com/golang-jwt/jwt/v5 v5.2.1
	github.com/minio/minio-go/v7 v7.0.80
	go.opentelemetry.io/otel v1.32.0
	go.opentelemetry.io/otel/trace v1.32.0
	go.opentelemetry.io/otel/sdk v1.32.0
	go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp v1.32.0
)
