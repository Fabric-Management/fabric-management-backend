# 🏭 Fabric Management System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Modern, maintainable, and production-ready fabric management system**

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

# Start with Docker
make deploy

# Or manually
docker-compose up -d
mvn clean install
mvn spring-boot:run
```

## 📁 Project Structure

```
fabric-management-backend/
├── docs/                      # Documentation
│   ├── analysis/             # Analysis reports
│   │   ├── SPRING_BOOT_BEST_PRACTICES_ANALYSIS.md
│   │   └── MICROSERVICE_DEVELOPMENT_ANALYSIS.md
│   ├── architecture/         # Architecture documentation
│   ├── api/                  # API documentation
│   ├── deployment/           # Deployment guides
│   └── development/          # Development guides
├── services/                 # Microservices
│   ├── user-service/
│   ├── company-service/
│   └── contact-service/
├── shared/                   # Shared modules
├── scripts/                  # Utility scripts
│   ├── deploy.sh            # Main deployment script
│   ├── run-migrations.sh    # Database migration script
│   └── docker-entrypoint.sh # Docker entrypoint
├── monitoring/              # Monitoring configuration
├── docker-compose.yml       # Docker infrastructure
└── Makefile                # Build automation
```

## 📊 Current Status & Roadmap

### Current Issues (To Be Fixed)

- **Over-engineering**: Unnecessary CQRS pattern (42 classes to be removed)
- **Code Quality**: 32% compliance with Spring Boot best practices
- **Testing**: Only 15% test coverage
- **Security**: Passwords stored in plain text

### 6-Week Improvement Plan

1. **Week 1-2**: Remove CQRS, standardize DTOs
2. **Week 3-4**: Implement Spring Boot best practices
3. **Week 5-6**: Security fixes and testing

See [SPRING_BOOT_BEST_PRACTICES_ANALYSIS.md](docs/analysis/SPRING_BOOT_BEST_PRACTICES_ANALYSIS.md) for detailed analysis.

## 🛠️ Technology Stack

- **Backend**: Java 21, Spring Boot 3.5.5
- **Database**: PostgreSQL 15, Flyway migrations
- **Caching**: Redis 7
- **Messaging**: Apache Kafka
- **Containerization**: Docker, Docker Compose
- **Build**: Maven

## 📚 Documentation

| Document                                                                                    | Description                              |
| ------------------------------------------------------------------------------------------- | ---------------------------------------- |
| [Spring Boot Best Practices Analysis](docs/analysis/SPRING_BOOT_BEST_PRACTICES_ANALYSIS.md) | Code quality and best practices analysis |
| [Microservice Development Analysis](docs/analysis/MICROSERVICE_DEVELOPMENT_ANALYSIS.md)     | Microservice architecture analysis       |
| [Architecture Guide](docs/architecture/README.md)                                           | System architecture overview             |
| [API Documentation](docs/api/README.md)                                                     | REST API specifications                  |
| [Deployment Guide](docs/deployment/README.md)                                               | Production deployment instructions       |
| [Development Guide](docs/development/README.md)                                             | Development setup and guidelines         |

## 🔧 Available Commands

```bash
# Build & Deploy
make build          # Build all services
make deploy         # Deploy with Docker
make down           # Stop all services
make restart        # Restart services

# Development
make test           # Run tests
make logs           # View logs
make status         # Check service status
make health         # Health check

# Database
make db-migrate     # Run migrations
make db-backup      # Backup database
make db-shell       # Open PostgreSQL shell

# Cleanup
make clean          # Clean build artifacts
make prune          # Docker system prune
```

## 🏗️ Services

| Service         | Port | Description                        |
| --------------- | ---- | ---------------------------------- |
| User Service    | 8081 | User management and authentication |
| Company Service | 8083 | Company and tenant management      |
| Contact Service | 8082 | Contact information management     |
| PostgreSQL      | 5433 | Primary database                   |
| Redis           | 6379 | Caching layer                      |
| Kafka           | 9092 | Event streaming                    |

## 🔐 Security Notes

⚠️ **Current security issues being addressed:**

- Password encoding implementation in progress
- JWT token management needs improvement
- API Gateway implementation pending

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Integration tests
mvn verify
```

Current test coverage: **15%** (Target: 80%)

## 📈 Performance Metrics

| Metric              | Current | Target |
| ------------------- | ------- | ------ |
| Startup Time        | 30s     | 5s     |
| Memory Usage        | 1GB     | 256MB  |
| Response Time (p95) | 500ms   | 100ms  |
| Test Coverage       | 15%     | 80%    |

## 🤝 Contributing

1. Follow Spring Boot best practices
2. Write tests for new features
3. Update documentation
4. Use conventional commits

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For issues and questions:

- Check [documentation](docs/)
- Create an issue on GitHub
- Contact the development team

---

**Version:** 1.0.0  
**Last Updated:** October 2025  
**Status:** Under active refactoring (see roadmap)
