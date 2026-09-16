# 🏭 Fabric Management System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Modern, maintainable, and production-ready fabric management system**

## ⚠️ **CRITICAL: NO USERNAME IN THIS PROJECT** ⚠️

```
╔════════════════════════════════════════════════════════════╗
║  🚫 THIS PROJECT DOES NOT USE USERNAME!                   ║
║  ✅ Authentication: contactValue (email/phone)            ║
║  ✅ Identification: userId (UUID)                          ║
║  ✅ JWT 'sub' claim: userId (UUID string)                 ║
╚════════════════════════════════════════════════════════════╝
```

## 📋 Overview

Fabric Management System is a comprehensive platform for fabric manufacturing, inventory management, and order processing built with Spring Boot best practices and clean architecture principles.

## 🚀 Quick Start

### Prerequisites

- Java 21+ (recommended: `brew install openjdk@21` on macOS)
- Maven 3.9+ (or use `./mvnw` – Maven wrapper included)
- Docker & Docker Compose
- PostgreSQL 16 (via Docker)

> **macOS:** Makefile and pre-commit hooks auto-detect `JAVA_HOME` for Homebrew OpenJDK. If `mvn` is not in PATH, use `./mvnw` or `make format` (uses wrapper).
>
> **Local setup without Docker:** See [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md).

### Installation

```bash
# Clone repository
git clone https://github.com/your-org/fabric-management-backend.git
cd fabric-management-backend

# Initial setup
make setup           # Create .env file
# Edit .env with your values

# Start infrastructure
make dev             # Start PostgreSQL

# Run application
make app-run         # Run Spring Boot app
```

## 📁 Project Structure

```
fabric-management-backend/
├── docs/                    # 📚 Documentation
├── scripts/                 # 🛠️ Utility scripts
├── src/                     # 📦 Source code (modular monolith)
└── docker-compose.yml       # 🐳 Docker infrastructure
```

## 🛠️ Technology Stack

- **Backend**: Java 21, Spring Boot 3.5.5
- **Database**: PostgreSQL 16, Flyway migrations
- **Messaging**: Apache Kafka (optional)
- **Containerization**: Docker, Docker Compose
- **Build**: Maven

## 🏛️ Architecture

**Modular Monolith** with Clean Architecture principles:

- ✅ **Domain-Driven Design (DDD)** - Business logic organized around domain concepts
- ✅ **Clean Architecture** - Clear separation of concerns
- ✅ **SOLID Principles** - Object-oriented design principles
- ✅ **Event-Driven** - Spring Events for inter-module communication

## 📚 API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html (when running)
- **Actuator Health**: http://localhost:8080/actuator/health

## 🔧 Available Commands

```bash
# Infrastructure
make up              # Start all infrastructure
make down            # Stop all services

# Application
make build           # Build application without tests
make run             # Run application with the local profile

# Testing
make test            # Run unit tests
make verify          # Run unit + integration tests and generate coverage
make verify-coverage # Run all tests and enforce the coverage baseline

# Database
make db-migrate      # Run migrations
make db-shell        # Open PostgreSQL shell

# Development
make logs            # View Docker service logs
make format          # Format code
make lint            # Blocking format + Checkstyle + SpotBugs checks
```

See `make help` for all available commands. **Code quality & automatic error detection:** [docs/CODE_QUALITY.md](docs/CODE_QUALITY.md).
The CI gates and container-release policy are documented in [docs/CI_CD.md](docs/CI_CD.md).

### PostgreSQL support and local-volume upgrade

FabricOS's first deployment target is PostgreSQL 16. Other PostgreSQL majors are unsupported until
they are verified separately. The exact Testcontainers and local Compose image is authored once as
`postgres.image` in `pom.xml`; the Compose image line is generated with:

```bash
python3 -B scripts/postgres_image.py --write
```

This keeps a bare `docker compose up` working without an image-related flag or `.env` entry.

A named `postgres_data` volume created by PostgreSQL 15 cannot be started directly with PostgreSQL
16. Before starting the upgraded image, choose explicitly:

- If the local data is disposable, remove the volume yourself with the destructive `make db-reset`
  command.
- If the data must be retained, back it up first and use a supported `pg_upgrade` procedure or a
  dump/restore into a fresh PostgreSQL 16 volume.

No setup, generation, or guard command removes a volume automatically.

Run `make build-4-verify` for the complete PostgreSQL verification and evidence archive. It
preserves logs and XML reports outside Maven's `target/` directory before a later `clean` can remove
them.

## 🔐 Security Features

- **JWT Authentication**: Token-based authentication
- **Rate Limiting**: Endpoint-specific throttling
- **Brute Force Protection**: Login attempt tracking
- **UUID Type Safety**: 100% compliance, no String IDs
- **No Hardcoded Values**: All via environment variables

## 🧪 Testing

```bash
# Run all tests
make verify

# Run all tests with the ratcheted coverage gate
make verify-coverage
```

## 🔍 Code Quality & Error Detection

Format, Checkstyle, SpotBugs, OWASP dependency check, and pre-commit hooks run automatically. See **[docs/CODE_QUALITY.md](docs/CODE_QUALITY.md)** for details.

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Version:** 2.0.0  
**Status:** 🚧 Active Development
