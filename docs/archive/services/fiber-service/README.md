# 🧵 Fiber Service

**Version:** 1.0.0  
**Status:** ✅ Production Ready  
**Port:** 8094

---

## 🎯 Purpose

Fiber Service manages the fabric fiber catalog including:

- Natural fibers (Cotton, Wool, Silk, etc.)
- Synthetic fibers (Polyester, Nylon, etc.)
- Blend compositions
- Fiber properties and validation

---

## ⚡ Quick Start

```bash
# Local development
mvn spring-boot:run

# Docker
docker-compose up fiber-service

# Health check
curl http://localhost:8094/actuator/health
```

---

## 📊 Key Features

✅ **Global Fiber Catalog** - Shared across all tenants  
✅ **Blend Validation** - Automatic composition validation  
✅ **Default Fibers** - Pre-seeded industry-standard fibers  
✅ **Property Management** - Physical/chemical properties  
✅ **Cache-First** - Redis caching for fast lookups  
✅ **Event-Driven** - Kafka events for fiber changes

---

## 🔗 API Endpoints

| Endpoint                           | Method | Auth     | Purpose              |
| ---------------------------------- | ------ | -------- | -------------------- |
| `/api/v1/fibers`                   | POST   | Admin    | Create fiber         |
| `/api/v1/fibers/blend`             | POST   | Admin    | Create blend         |
| `/api/v1/fibers/{id}`              | GET    | Auth     | Get fiber            |
| `/api/v1/fibers`                   | GET    | Auth     | List fibers          |
| `/api/v1/fibers/default`           | GET    | Public   | Default fibers       |
| `/api/v1/fibers/search`            | GET    | Auth     | Search fibers        |
| `/api/v1/fibers/internal/validate` | POST   | Internal | Validate composition |

---

## 📖 Documentation

Full documentation: `docs/services/fabric-fiber-service/`

- Architecture
- Testing guide
- API reference
- World fiber catalog

---

**Built with:** Spring Boot 3.5.5 | Java 21 | PostgreSQL | Redis | Kafka
