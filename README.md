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

- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL 15+ (via Docker)

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
├── scripts/                 # 🛠️ Utility scripts
├── src/                     # 📦 Source code (modular monolith)
└── docker-compose.yml       # 🐳 Docker infrastructure
```

## 🛠️ Technology Stack

- **Backend**: Java 21, Spring Boot 3.5.5
- **Database**: PostgreSQL 15, Flyway migrations
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
make dev             # Start PostgreSQL
make up              # Start all infrastructure
make down            # Stop all services

# Application
make app-build       # Build application
make app-run         # Run application

# Testing
make test            # Run unit tests
make coverage        # Generate coverage report

# Database
make db-migrate      # Run migrations
make db-shell        # Open PostgreSQL shell

# Development
make health          # Check application health
make logs            # View logs
make format          # Format code
make lint            # Code quality checks
```

See `make help` for all available commands.

## 🔐 Security Features

- **JWT Authentication**: Token-based authentication
- **Rate Limiting**: Endpoint-specific throttling
- **Brute Force Protection**: Login attempt tracking
- **UUID Type Safety**: 100% compliance, no String IDs
- **No Hardcoded Values**: All via environment variables

## 🧪 Testing

```bash
# Run all tests
make test

# Run with coverage
make coverage
```

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Version:** 2.0.0  
**Status:** ✅ Production Ready
