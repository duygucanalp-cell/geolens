# syntax=docker/dockerfile:1
# GeoLens Java — Multi-stage Dockerfile
# Targets: api (varsayılan), worker, scheduler
#
# Build context: platform/ (docker-compose.java.yml'de context: .)
#   docker build -f java/Dockerfile --target api .
# Not: entrypoint.sh platform/docker/ altındadır — context kökü platform/ olmalıdır.

# ============================================================
# Stage 1: Maven build (jOOQ codegen + Spring Boot repackage)
# ============================================================
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

COPY java/pom.xml ./pom.xml
RUN mvn -q -B dependency:go-offline

COPY java/src ./src
RUN mvn -q -B package -DskipTests

# ============================================================
# Stage 2: Runtime (secrets entrypoint dahil)
# ============================================================
FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/geolens-*.jar /app/app.jar
COPY docker/entrypoint.sh /usr/local/bin/geolens-entrypoint
RUN chmod +x /usr/local/bin/geolens-entrypoint

# ============================================================
# Stage 3: API server (Go cmd/api karşılığı)
# ============================================================
FROM runtime AS api

ENV SPRING_PROFILES_ACTIVE=api
EXPOSE 8080

ENTRYPOINT ["geolens-entrypoint"]
CMD ["java", "-jar", "/app/app.jar"]

# ============================================================
# Stage 4: Worker (Go cmd/worker karşılığı — Redis Stream tüketicileri)
# ============================================================
FROM runtime AS worker

ENV SPRING_PROFILES_ACTIVE=worker

ENTRYPOINT ["geolens-entrypoint"]
CMD ["java", "-jar", "/app/app.jar"]

# ============================================================
# Stage 5: Scheduler (Go cmd/scheduler karşılığı — outbox dispatcher + panel cron + digest)
# ============================================================
FROM runtime AS scheduler

ENV SPRING_PROFILES_ACTIVE=scheduler

ENTRYPOINT ["geolens-entrypoint"]
CMD ["java", "-jar", "/app/app.jar"]
