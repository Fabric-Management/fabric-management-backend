# Fabric Management Backend Developer Guide

> **READ FIRST:** FabricOS is a UK product — all sample/seed/test data is British, no Turkish names, British English first and Turkish second. Full rule at the top of `../AGENTS.md`.

## Build & Run Commands
- Build project (skip tests): `./mvnw clean install -DskipTests`
- Run the application: `docker compose up -d --build` (or `make run`). Docker is the only way to
  run it locally (application-local.yml and `./mvnw spring-boot:run` were retired on 2026-10-31);
  the app is served on http://localhost:8080. There is no default profile: docker-compose.yml
  sets `docker`, production sets `prod`, and every Spring test declares `@ActiveProfiles("test")`.
  Starting with no profile fails at once (`RequireActiveProfileConfig`).
- Verify it starts: `make check-run` (builds the image and waits for the health check).
- Format code: `./mvnw fmt:format`
- Run bug detection: `./mvnw spotbugs:check`

## Testing Commands
- Run all tests: `./mvnw test`
- Run architecture tests: `./mvnw test -Dtest="com.fabricmanagement.architecture.*ArchTest"`
- Run integration tests: `./mvnw test -Dtest="*IT"` or `./mvnw verify`

## Technology Stack & Guidelines
- Java 21, Spring Boot 3.2+, PostgreSQL + Flyway.
- Follow the modular structure (`api/`, `app/`, `domain/`, `dto/`, `infra/`, `mapper/`).
- Every JPA entity must extend `BaseEntity` or `BaseJunctionEntity`.
- Do not expose entities directly; use DTOs (`record` format).
- Use HSL tailored/harmonious colors and premium aesthetics for frontend interactions.
