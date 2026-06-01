# Fabric Management Backend Developer Guide

## Build & Run Commands
- Build project (skip tests): `./mvnw clean install -DskipTests`
- Run local application: `./mvnw spring-boot:run`
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
