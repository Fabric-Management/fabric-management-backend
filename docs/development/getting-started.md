# Getting Started

## Development Environment Setup

### 1. Install Required Tools

#### Java 21

```bash
# MacOS
brew install openjdk@21

# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Windows (using Chocolatey)
choco install openjdk21

# Verify installation
java -version
```

#### Docker

- Download from [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- Verify installation:
```bash
docker --version
docker-compose --version
```

#### Maven

```bash
# MacOS
brew install maven

# Ubuntu/Debian
sudo apt install maven

# Windows (using Chocolatey)
choco install maven

# Verify installation
mvn -version
```

#### IDE Setup

**Recommended: IntelliJ IDEA**

1. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community or Ultimate)
2. Install required plugins:
    - **Lombok Plugin**: `File → Settings → Plugins → Search "Lombok"`
    - **Spring Boot Plugin**: Pre-installed in Ultimate, available in Marketplace for Community
    - **Docker Plugin**: For Docker integration
3. Enable annotation processing:
    - `File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors`
    - Check "Enable annotation processing"

**Alternative: Visual Studio Code**

1. Install [VS Code](https://code.visualstudio.com/)
2. Install extensions:
    - Extension Pack for Java
    - Spring Boot Extension Pack
    - Lombok Annotations Support
    - Docker

### 2. Database Setup

Start PostgreSQL using Docker Compose:

```bash
# From project root directory
docker-compose up -d

# Verify PostgreSQL is running
docker ps

# Check logs if needed
docker-compose logs -f postgres
```

This will:
- Start PostgreSQL 15 on port **5433** (to avoid conflicts with local installations)
- Create user: `user_service` with password: `password`
- Create database: `user_db`
- Initialize schema with Flyway migrations

**Connection Details:**
```yaml
Host: localhost
Port: 5433
Database: user_db
Username: user_service
Password: password
```

### 3. Build the Project

```bash
# From project root directory
# Install dependencies and build all modules
./mvnw clean install

# Build without running tests (faster)
./mvnw clean install -DskipTests

# Build specific module only
./mvnw clean install -pl services/user-service -am
```

### 4. Run User Service

```bash
# Navigate to user service
cd services/user-service

# Run with Maven
../../mvnw spring-boot:run

# Or run with specific profile
../../mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or run the JAR directly
java -jar target/user-service-1.0.0.jar
```

Service will start on port **8081**.

**Verify the service is running:**
```bash
# Health check
curl http://localhost:8081/actuator/health

# Should return:
# {"status":"UP"}
```

## API Testing

### Create User

```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "username": "johndoe"
  }'
```

**Expected Response:**
```json
{
  "id": "generated-uuid",
  "firstName": "John",
  "lastName": "Doe",
  "username": "johndoe",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### Get User

```bash
curl -X GET http://localhost:8081/api/v1/users/{userId} \
  -H "X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000"
```

### Get All Users (Paginated)

```bash
curl -X GET "http://localhost:8081/api/v1/users?page=0&size=10" \
  -H "X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000"
```

### Update User

```bash
curl -X PUT http://localhost:8081/api/v1/users/{userId} \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000" \
  -d '{
    "firstName": "Jane",
    "lastName": "Smith"
  }'
```

### Delete User

```bash
curl -X DELETE http://localhost:8081/api/v1/users/{userId} \
  -H "X-Tenant-ID: 123e4567-e89b-12d3-a456-426614174000"
```

## Using Postman or Insomnia

For a better API testing experience, import the following collection:

**Postman Collection:** `docs/postman/user-service.postman_collection.json`

Key settings:
- Base URL: `http://localhost:8081`
- Headers:
    - `X-Tenant-ID`: Your tenant UUID
    - `Content-Type`: `application/json`

## Common Issues & Solutions

### Port 5432 Already in Use

**Problem:** Default PostgreSQL port conflict  
**Solution:** We use port 5433 in docker-compose.yml
```bash
# Check what's using port 5432
lsof -i :5432  # MacOS/Linux
netstat -ano | findstr :5432  # Windows
```

### Flyway Migration Failed

**Problem:** Database migration errors  
**Solution:**
```bash
# Check PostgreSQL logs
docker-compose logs postgres

# Reset database (WARNING: Deletes all data)
docker-compose down -v
docker-compose up -d

# Check Flyway status
./mvnw flyway:info -pl services/user-service
```

### MapStruct Not Generating Mappers

**Problem:** Mapper implementations not created  
**Solution:**
1. Enable annotation processing in IDE
2. Clean and rebuild:
```bash
./mvnw clean compile
```
3. Check target/generated-sources/annotations

### Application Won't Start

**Problem:** Spring Boot startup failure  
**Solution:**
```bash
# Check for port conflicts
lsof -i :8081

# Verify PostgreSQL is running
docker-compose ps

# Check application logs
./mvnw spring-boot:run | grep ERROR
```

### Maven Build Failures

**Problem:** Dependencies not downloading  
**Solution:**
```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Force update dependencies
./mvnw clean install -U
```

## Development Workflow

### 1. Create Feature Branch

```bash
# Update main branch
git checkout main
git pull origin main

# Create feature branch
git checkout -b feature/your-feature-name
```

### 2. Implement Feature

Follow Clean Architecture principles:
- **Domain Layer:** Business logic and entities
- **Application Layer:** Use cases and services
- **Infrastructure Layer:** Database, external services
- **Presentation Layer:** REST controllers

### 3. Write Tests

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify

# Run specific test class
./mvnw test -Dtest=UserServiceTest

# Generate coverage report
./mvnw clean test jacoco:report
# Report available at: target/site/jacoco/index.html
```

### 4. Update Documentation

- Update API documentation if endpoints changed
- Update README if setup process changed
- Add JavaDoc for public methods

### 5. Create Pull Request

```bash
# Commit changes
git add .
git commit -m "feat: add user validation"

# Push to remote
git push origin feature/your-feature-name
```

Then create PR on GitHub/GitLab.

## Code Style

### Google Java Style Guide

We follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). Key points:

- **Indentation:** 2 spaces (no tabs)
- **Line length:** 100 characters max
- **Braces:** Egyptian style (opening brace on same line)

### Naming Conventions

```java
// Classes: PascalCase
public class UserService { }

// Methods & variables: camelCase
public void createUser(String userName) { }

// Constants: UPPER_SNAKE_CASE
public static final int MAX_RETRY_COUNT = 3;

// Packages: lowercase
package com.fabric.userservice.domain;
```

### Code Quality Checklist

- [ ] Meaningful variable and method names
- [ ] Self-documenting code
- [ ] JavaDoc for all public APIs
- [ ] No commented-out code
- [ ] Proper exception handling
- [ ] Unit tests for business logic
- [ ] Integration tests for APIs

### Example JavaDoc

```java
/**
 * Creates a new user in the system.
 * 
 * @param command the user creation command containing user details
 * @return the created user entity
 * @throws UserAlreadyExistsException if username already exists
 * @throws InvalidUserDataException if user data validation fails
 */
public User createUser(CreateUserCommand command) {
    // Implementation
}
```

## Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Clean Architecture Guide](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Domain-Driven Design Reference](https://www.domainlanguage.com/ddd/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Documentation](https://docs.docker.com/)

## Next Steps

1. ✅ Set up development environment
2. ✅ Run User Service successfully
3. 📖 Read [Architecture Overview](../architecture/overview.md)
4. 🏗️ Explore the codebase structure
5. 🧪 Run and understand existing tests
6. 💡 Start implementing your first feature

## Need Help?

- Check the [FAQ](../faq.md)
- Search existing [Issues](https://github.com/yourusername/fabric-management-system/issues)
- Ask in our [Discord/Slack Channel](#)
- Email: dev-support@fabricmanagement.com

---

**Happy Coding!** 🚀