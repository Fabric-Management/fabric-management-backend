# Fabric Management System

A multi-tenant fabric management system built with microservices architecture, implementing Domain-Driven Design (DDD) and Clean Architecture principles.

## 🏗️ Architecture Overview

This project follows:
- **Microservices Architecture**: Each service is independently deployable
- **Domain-Driven Design (DDD)**: Business logic is at the center
- **Clean Architecture**: Clear separation of concerns
- **Hexagonal Architecture**: Ports and Adapters pattern
- **CQRS**: Command Query Responsibility Segregation

## 🚀 Quick Start

### Prerequisites

- Java 21
- Docker & Docker Compose
- Maven 3.8+
- PostgreSQL 15 (via Docker)

### Running the Application

1. **Clone the repository:**
```bash
git clone https://github.com/yourusername/fabric-management-system.git
cd fabric-management-system
```

2. **Start the infrastructure:**
```bash
docker-compose up -d
```

3. **Build the project:**
```bash
./mvnw clean install
```

4. **Run User Service:**
```bash
cd services/user-service
../../mvnw spring-boot:run
```

The service will be available at `http://localhost:8081`

## 📦 Project Structure

```
fabric-management-system/
├── common/                 # Shared libraries
│   ├── common-core/       # Core domain classes
│   ├── common-persistence/# JPA base configurations
│   └── common-web/        # Web/REST utilities
├── services/              # Microservices
│   ├── user-service/      # User management
│   ├── contact-service/   # Contact information (planned)
│   └── auth-service/      # Authentication (planned)
├── infrastructure/        # Infrastructure services (planned)
│   ├── api-gateway/       
│   └── service-discovery/ 
└── deployment/           # Deployment configurations
```

## 🛠️ Technology Stack

- **Framework:** Spring Boot 3.2.0, Spring Cloud 2023.0.0
- **Language:** Java 21
- **Database:** PostgreSQL 15
- **Migration:** Flyway 10.15.0
- **ORM:** Spring Data JPA (Hibernate)
- **Build Tool:** Maven
- **Container:** Docker
- **Message Broker:** RabbitMQ (planned)

## 📖 Documentation

- [Architecture Overview](docs/architecture.md)
- [Getting Started Guide](docs/getting-started.md)
- [API Documentation](docs/api.md)
- [Project Structure](docs/structure.md)

## 🧪 Testing

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify

# Run with test coverage
./mvnw clean test jacoco:report
```

## 🚀 Development

### Building the Project

```bash
# Clean build
./mvnw clean install

# Skip tests during build
./mvnw clean install -DskipTests

# Build specific module
./mvnw clean install -pl services/user-service -am
```

### Running Services Locally

Each service can be run independently:

```bash
# User Service (Port 8081)
cd services/user-service && ../../mvnw spring-boot:run

# Contact Service (Port 8082) - Coming soon
cd services/contact-service && ../../mvnw spring-boot:run

# Auth Service (Port 8083) - Coming soon
cd services/auth-service && ../../mvnw spring-boot:run
```

### Docker Development

```bash
# Build Docker images
docker-compose build

# Start all services
docker-compose up

# Start specific service
docker-compose up user-service

# View logs
docker-compose logs -f user-service
```

## 📊 Service Endpoints

### User Service
- **Base URL:** `http://localhost:8081`
- **Health Check:** `/actuator/health`
- **API Docs:** `/swagger-ui.html`

### Planned Services
- **Contact Service:** Port 8082 (Coming soon)
- **Auth Service:** Port 8083 (Coming soon)
- **API Gateway:** Port 8080 (Coming soon)

## 🔧 Configuration

Configuration files are located in:
- `services/{service-name}/src/main/resources/application.yml`
- Environment-specific: `application-{profile}.yml`

### Environment Variables

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=fabric_db
DB_USER=fabric_user
DB_PASSWORD=fabric_pass

# Service Configuration
SERVER_PORT=8081
SPRING_PROFILES_ACTIVE=dev
```

## 🤝 Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and development process.

### Development Workflow

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📈 Roadmap

- [x] User Service implementation
- [x] Domain-Driven Design structure
- [x] Clean Architecture implementation
- [ ] Contact Service
- [ ] Authentication Service
- [ ] API Gateway
- [ ] Service Discovery (Eureka)
- [ ] Message Queue (RabbitMQ)
- [ ] Distributed Tracing (Zipkin)
- [ ] Monitoring (Prometheus + Grafana)
- [ ] CI/CD Pipeline
- [ ] Kubernetes deployment

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Authors

- **Your Name** - *Initial work* - [YourGitHub](https://github.com/yourusername)

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- Domain-Driven Design community
- Clean Architecture principles by Robert C. Martin

## 📞 Support

For support, email support@fabricmanagement.com or create an issue in this repository.

---

**Note:** This project is under active development. Some features mentioned in this README are planned for future releases.