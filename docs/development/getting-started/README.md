# 🚀 Getting Started Guide

## 📋 Prerequisites

Before starting development, ensure you have the following installed:

- **Java 17+**: OpenJDK or Oracle JDK
- **Maven 3.8+**: Build tool
- **Docker & Docker Compose**: Containerization
- **PostgreSQL 13+**: Database
- **Redis 6+**: Caching and session management
- **Apache Kafka 2.8+**: Event streaming

## 🏗️ Project Setup

### **1. Clone Repository**

```bash
git clone <repository-url>
cd fabric-management-backend
```

### **2. Environment Configuration**

Create `.env` file in project root:

```bash
# Database Configuration
POSTGRES_DB=fabric_management
POSTGRES_USER=fabric_user
POSTGRES_PASSWORD=fabric_password

# Redis Configuration
REDIS_PASSWORD=redis_password

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# JWT Configuration
JWT_SECRET=your-secret-key-here

# Spring Profiles
SPRING_PROFILES_ACTIVE=local
```

### **3. Start Infrastructure Services**

```bash
# Start PostgreSQL, Redis, Kafka, and Zookeeper
docker-compose up -d postgres-db redis zookeeper kafka

# Wait for services to be healthy
docker-compose ps
```

### **4. Build and Start Services**

```bash
# Build all services
./mvnw clean install

# Start Identity Service
cd services/identity-service
./mvnw spring-boot:run

# Start User Service (in another terminal)
cd services/user-service
./mvnw spring-boot:run
```

## 🔧 Development Workflow

### **1. Service Development**

- Each service has its own module
- Follow Clean Architecture principles
- Write tests for all business logic
- Use domain events for inter-service communication

### **2. Database Migrations**

- Use Flyway for database migrations
- Place migration files in `src/main/resources/db/migration/`
- Follow naming convention: `V{version}__{description}.sql`

### **3. Event Development**

- Define domain events in domain layer
- Publish events from application services
- Consume events in infrastructure layer
- Test event publishing and consumption

### **4. API Development**

- Use OpenAPI/Swagger for API documentation
- Follow RESTful conventions
- Implement proper error handling
- Add request/response validation

## 🧪 Testing

### **Unit Tests**

```bash
# Run unit tests for specific service
cd services/identity-service
./mvnw test

# Run all tests
./mvnw test
```

### **Integration Tests**

```bash
# Run integration tests with test containers
./mvnw test -Dspring.profiles.active=test
```

### **End-to-End Tests**

```bash
# Start all services
docker-compose up -d

# Run E2E tests
./mvnw test -Dspring.profiles.active=e2e
```

## 📊 Monitoring and Debugging

### **Health Checks**

- Identity Service: `http://localhost:8081/api/identity/actuator/health`
- User Service: `http://localhost:8082/api/user/actuator/health`

### **API Documentation**

- Identity Service: `http://localhost:8081/api/identity/swagger-ui.html`
- User Service: `http://localhost:8082/api/user/swagger-ui.html`

### **Logs**

```bash
# View service logs
docker-compose logs -f identity-service
docker-compose logs -f user-service

# View Kafka logs
docker-compose logs -f kafka
```

## 🔍 Common Development Tasks

### **Adding New Domain Event**

1. Define event in domain layer
2. Publish event from application service
3. Consume event in infrastructure layer
4. Write tests for event flow

### **Adding New API Endpoint**

1. Define DTOs for request/response
2. Implement controller method
3. Add business logic in application service
4. Write tests for endpoint
5. Update API documentation

### **Database Schema Changes**

1. Create Flyway migration script
2. Update entity classes
3. Update repository methods
4. Write tests for new functionality

## 🚀 Deployment

### **Local Development**

```bash
# Start all services
docker-compose up -d

# Check service status
docker-compose ps
```

### **Production Deployment**

```bash
# Build production images
docker-compose -f docker-compose.prod.yml build

# Deploy to production
docker-compose -f docker-compose.prod.yml up -d
```

## 🔧 Troubleshooting

### **Common Issues**

#### **Service Won't Start**

- Check port availability
- Verify database connectivity
- Check environment variables
- Review service logs

#### **Database Connection Issues**

- Verify PostgreSQL is running
- Check connection parameters
- Verify database exists
- Check network connectivity

#### **Kafka Connection Issues**

- Verify Kafka is running
- Check bootstrap servers configuration
- Verify topic creation
- Check consumer group status

#### **Event Processing Issues**

- Check Kafka connectivity
- Verify event serialization
- Check consumer group status
- Review event processing logs

### **Debug Commands**

```bash
# Check service health
curl http://localhost:8081/api/identity/actuator/health

# Check Kafka topics
docker exec -it fabric-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Check consumer groups
docker exec -it fabric-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

## 📚 Additional Resources

- [Architecture Documentation](../architecture/README.md)
- [Service Integration Guide](../integration/identity-user-integration.md)
- [API Documentation](../user-guides/api/README.md)
- [Deployment Guide](../deployment/README.md)
- [Coding Standards](../development/coding-standards.md)
