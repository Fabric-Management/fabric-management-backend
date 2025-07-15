# How to Run - Fabric Management Backend

## Prerequisites

### Required Software
- **Java 21** or higher
- **Maven 3.9+**
- **Docker Desktop** (for Mac/Windows) or Docker Engine (for Linux)
- **Docker Compose**
- **PostgreSQL client** (optional, for debugging)

### Verify Installation
```bash
# Check Java version
java -version

# Check Maven version
mvn -version

# Check Docker
docker --version
docker-compose --version
```

## Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/Fabric-Management/fabric-management-backend.git
cd fabric-management-backend
```

### 2. Start Infrastructure Services
```bash
cd docker
docker-compose up -d
```

This will start:
- **PostgreSQL** (port 5432)
- **PgAdmin** (port 5050)
- **Keycloak** (port 8080)
- **RabbitMQ** (port 5672, Management UI: 15672)

### 3. Verify Services
```bash
# Check running containers
docker ps

# Check PostgreSQL logs
docker logs fabric_postgres
```

### 4. Start Microservices

#### User Service
```bash
cd service/user-service
./mvnw spring-boot:run
```
- Runs on: http://localhost:8081

#### Contact Service
```bash
cd service/contact-service
./mvnw spring-boot:run
```
- Runs on: http://localhost:8082

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| User Service | 8081 | User management API |
| Contact Service | 8082 | Contact management API |
| PostgreSQL | 5432 | Database |
| PgAdmin | 5050 | Database management UI |
| Keycloak | 8080 | Identity provider |
| RabbitMQ | 5672 | Message broker |
| RabbitMQ Management | 15672 | RabbitMQ UI |

## Database Access

### Via PgAdmin
1. Open http://localhost:5050
2. Login:
    - Email: `admin@fabric.com`
    - Password: `admin123`
3. Add server:
    - Host: `postgres`
    - Port: `5432`
    - Username: `postgres`
    - Password: `postgres_admin`

### Via Command Line
```bash
# Connect to PostgreSQL
docker exec -it fabric_postgres psql -U postgres -d fabric_management_db

# Or from host
psql -h localhost -p 5432 -U postgres -d fabric_management_db
# Password: postgres_admin
```

## Common Issues

### Port Conflicts
If you have PostgreSQL installed locally (e.g., via Homebrew):
```bash
# Stop local PostgreSQL
brew services stop postgresql@14

# Or change Docker PostgreSQL port in docker-compose.yml
ports:
  - "5433:5432"
```

### Port Already in Use
```bash
# Find and kill process using a port
lsof -i :8081
kill -9 $(lsof -t -i:8081)
```

### Database Connection Issues
Ensure only Docker PostgreSQL is running:
```bash
lsof -i :5432
```

## Development Workflow

### 1. Start All Services
```bash
# Start infrastructure
cd docker && docker-compose up -d

# Start microservices (in separate terminals)
cd service/user-service && ./mvnw spring-boot:run
cd service/contact-service && ./mvnw spring-boot:run
```

### 2. Stop All Services
```bash
# Stop microservices: Press Ctrl+C in each terminal

# Stop Docker services
cd docker && docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

### 3. View Logs
```bash
# Docker service logs
docker logs fabric_postgres
docker logs fabric_keycloak
docker logs fabric_rabbitmq

# Follow logs
docker logs -f fabric_postgres
```

## Testing

### Health Check
```bash
# User Service
curl http://localhost:8081/actuator/health

# Contact Service  
curl http://localhost:8082/actuator/health
```

### API Testing
```bash
# Example: Get all users
curl http://localhost:8081/api/users

# Example: Get all contacts
curl http://localhost:8082/api/contacts
```

## Configuration

### Application Properties
Each service has its own configuration in:
```
service/{service-name}/src/main/resources/application.properties
```

### Environment-Specific Configuration
Create `application-{profile}.properties` for different environments:
- `application-dev.properties`
- `application-prod.properties`

Run with specific profile:
```bash
./mvnw spring-boot:run -Dspring.profiles.active=dev
```

## Useful Commands

### Docker Commands
```bash
# View all containers
docker ps -a

# Stop all containers
docker stop $(docker ps -aq)

# Remove all containers
docker rm $(docker ps -aq)

# View Docker networks
docker network ls

# Inspect network
docker network inspect docker_fabric-network
```

### Maven Commands
```bash
# Clean and build
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Run specific service
mvn spring-boot:run

# Build Docker image
mvn spring-boot:build-image
```

## Production Deployment

For production deployment, refer to:
- [Kubernetes Deployment Guide](./kubernetes-deployment.md) (TODO)
- [AWS Deployment Guide](./aws-deployment.md) (TODO)

## Support

For issues or questions:
1. Check [Troubleshooting Guide](./troubleshooting-postgresql.md)
2. Review [Architecture Documentation](./architecture.md)
3. Create an issue on GitHub

---

*Last Updated: July 2025*