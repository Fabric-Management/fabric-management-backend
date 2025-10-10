# 🚀 Getting Started - Developer Onboarding

**Purpose:** Get up and running in 15 minutes  
**Last Updated:** October 10, 2025  
**Status:** ✅ Active

---

## 🎯 What You'll Learn

- ✅ Run the project locally (5 minutes)
- ✅ Create your first endpoint (5 minutes)
- ✅ Write and run tests (5 minutes)
- ✅ Hot reload development setup

---

## ⚡ PART 1: Quick Start (15 Minutes)

### Step 1: Prerequisites (2 min)

```bash
# Verify installations
java -version      # Java 21+
mvn -version       # Maven 3.9+
docker --version   # Docker 20+
```

### Step 2: Clone & Start Infrastructure (3 min)

```bash
# Clone
git clone <repo-url>
cd fabric-management-backend

# Start infrastructure only (PostgreSQL, Redis, Kafka)
docker-compose up -d

# Verify
docker-compose ps  # All should be "healthy"
```

### Step 3: Start Services Locally (5 min)

```bash
# Terminal 1: User Service (for JWT)
cd services/user-service
mvn spring-boot:run

# Terminal 2: Company Service
cd services/company-service
mvn spring-boot:run

# ✅ Services running!
# User: http://localhost:8081
# Company: http://localhost:8083
```

### Step 4: Test (5 min)

```bash
# Health check
curl http://localhost:8081/actuator/health
curl http://localhost:8083/actuator/health

# ✅ Both should return {"status":"UP"}
```

---

## 🔥 PART 2: First Endpoint (10 Minutes)

### Create Controller

```java
// services/user-service/src/main/java/.../api/controller/HelloController.java
@RestController
@RequestMapping("/api/v1/hello")
public class HelloController {

    @GetMapping
    public String sayHello() {
        return "Merhaba Fabric!";
    }

    @GetMapping("/{name}")
    public String sayHelloTo(@PathVariable String name) {
        return "Merhaba " + name + "!";
    }
}
```

### Test It

```bash
curl http://localhost:8081/api/v1/hello
# Output: Merhaba Fabric!

curl http://localhost:8081/api/v1/hello/Developer
# Output: Merhaba Developer!
```

### Write Test

```java
@WebMvcTest(HelloController.class)
class HelloControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSayHello() throws Exception {
        mockMvc.perform(get("/api/v1/hello"))
            .andExpect(status().isOk())
            .andExpect(content().string("Merhaba Fabric!"));
    }
}
```

### Run Tests

```bash
cd services/user-service
mvn test
# ✅ Tests passed!
```

---

## 🚀 PART 3: Hot Reload Development (Production Speed!)

### Why Local Development?

```
Docker Build: 10 minutes per change ❌
Local Dev: 5 seconds per change ✅

= 120x FASTER! 🔥
```

### Setup (One Time)

**1. Keep Infrastructure in Docker:**

```bash
# Start once, forget it
docker-compose up -d postgres redis kafka
```

**2. Run Services Locally:**

**IntelliJ IDEA (Recommended):**

```
1. Open services/company-service/pom.xml
2. Right-click CompanyServiceApplication.java
3. Run 'CompanyServiceApplication'
4. ✅ Auto-reload on save!
```

**VS Code:**

```
1. Install Java Extension Pack
2. F5 → Select Spring Boot App
3. ✅ Debug mode enabled
```

**Maven (Terminal):**

```bash
cd services/company-service
mvn spring-boot:run
# ✅ Manual restart needed for changes
```

### Configuration (Automatic!)

No environment variables needed! Spring Boot uses `local` profile automatically:

- Database: `localhost:5433` → Docker PostgreSQL
- Redis: `localhost:6379` → Docker Redis
- Kafka: `localhost:9092` → Docker Kafka
- JWT Secret: Pre-configured for development

### Hot Reload Tips

**Enable in IntelliJ:**

```
Settings → Build → Compiler
✅ "Build project automatically"

Settings → Advanced Settings
✅ "Allow auto-make to start"
```

**Add DevTools (pom.xml):**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

### Development Workflow

```
1. Make code change
2. Save file (Ctrl+S)
3. Wait 5 seconds (auto-reload)
4. Test in Postman
5. Repeat!

No Docker rebuild needed! 🚀
```

---

## 🧪 PART 4: Testing with Postman

### Get JWT Token

```http
POST http://localhost:8081/api/v1/users/auth/login
Content-Type: application/json

{
  "contactValue": "admin@example.com",
  "password": "your-password"
}
```

**Response:** Copy `accessToken`

### Import Collection

```
File: postman/Company-Management-Local.postman_collection.json

1. Import to Postman
2. Set variables:
   - authToken: <paste JWT>
   - baseUrl: http://localhost:8083
```

### Test Flow

```
1. CREATE Company
2. ADD Email Contact
3. ADD Phone Contact
4. ADD CEO User
5. ADD PURCHASER User

All use {{companyId}} automatically!
```

---

## 🛠️ Useful Commands

### Infrastructure

```bash
# Status
docker-compose ps

# Logs
docker-compose logs -f postgres

# Restart
docker-compose restart redis

# Stop all
docker-compose down
```

### Database

```bash
# Connect
docker exec -it postgres psql -U fabric_user -d fabric_management

# List tables
\dt

# Query
SELECT * FROM companies;

# Exit
\q
```

### Redis

```bash
# Connect
docker exec -it redis redis-cli

# List keys
KEYS *

# Clear cache
FLUSHALL
```

### Kafka

```bash
# List topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Watch messages
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic company-events \
  --from-beginning
```

---

## 🐛 Troubleshooting

### Port Already in Use

```bash
lsof -i :8083
kill -9 <PID>
```

### Database Connection Failed

```bash
# Check PostgreSQL
docker-compose ps postgres
docker-compose logs postgres

# Restart
docker-compose restart postgres
```

### Flyway Migration Error

```bash
# Reset migrations (dev only!)
docker exec postgres psql -U fabric_user -d fabric_management \
  -c "DELETE FROM flyway_schema_history WHERE version > '1';"
```

### Hot Reload Not Working

- Verify DevTools dependency in pom.xml
- Enable auto-make in IDE settings
- Restart IDE
- Check logs for reload confirmation

---

## 📚 Next Steps

After successful setup:

1. **Read**: [PRINCIPLES.md](./PRINCIPLES.md) - NO USERNAME, SOLID, UUID
2. **Read**: [MICROSERVICES_API_STANDARDS.md](./MICROSERVICES_API_STANDARDS.md) - API patterns
3. **Read**: [DATA_TYPES_STANDARDS.md](./DATA_TYPES_STANDARDS.md) - UUID compliance
4. **Code**: Start with simple CRUD endpoint
5. **Review**: Submit PR, get feedback

---

**Maintained By:** Development Team  
**Last Updated:** 2025-10-10  
**Status:** ✅ Active - Fast development ready
