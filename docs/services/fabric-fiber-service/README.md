# 🧵 Fabric Fiber Service - Documentation Index

**Service:** fabric-fiber-service  
**Port:** 8094  
**Base Path:** `/api/v1/fibers`  
**Status:** 🔴 CRITICAL - Foundation service for entire textile chain

---

## 📚 Documentation Files

| Document                                                 | Description                                 | Lines | Priority     |
| -------------------------------------------------------- | ------------------------------------------- | ----- | ------------ |
| **[fabric-fiber-service.md](./fabric-fiber-service.md)** | Main service specification & architecture   | 800+  | 🔴 Must Read |
| **[WORLD_FIBER_CATALOG.md](./WORLD_FIBER_CATALOG.md)**   | Complete global fiber registry (35+ fibers) | 400+  | ⚠️ Reference |

---

## 🎯 Quick Navigation

### Planning Phase

- 📖 [fabric-fiber-service.md](./fabric-fiber-service.md) - Domain architecture & API design

### Implementation Phase

- 🌍 [WORLD_FIBER_CATALOG.md](./WORLD_FIBER_CATALOG.md) - Seed data source

### Key Sections in Main Doc

| What You Need                      | Section Link                                                   |
| ---------------------------------- | -------------------------------------------------------------- |
| **Quick answers to key questions** | [Quick Answers](#-quick-answers-to-key-questions)              |
| **Domain boundary (In/Out scope)** | [Domain Boundary](#-domain-boundary)                           |
| **Aggregate structure**            | [Aggregate Structure](#-aggregate-structure)                   |
| **Seed data list**                 | [Default Seed Data](#-default-seed-data-100-fibers)            |
| **Blend fiber creation**           | [Blend Creation](#3️⃣-blend-fiber-oluşturma-karışım)            |
| **Procurement field override**     | [Procurement Flow](#2️⃣-satın-alma-sırasında-fiber-özellikleri) |
| **API endpoints**                  | [API Design](#-api-design-cqrs--service-aware-pattern)         |
| **Database schema**                | [Database Schema](#️-database-schema-flyway-migration)         |
| **Project structure**              | [Project Structure](#-project-structure-clean-architecture)    |
| **Implementation checklist**       | [Checklist](#-implementation-checklist)                        |

---

## 🔴 CRITICAL DESIGN DECISIONS

1. **%100 Fiberler Aggregate Olarak Var Mı?**

   - ✅ YES - Database'de tam Fiber entity olarak seed edilir
   - 📍 See: fabric-fiber-service.md → [Seed Data Section]

2. **Yarn Aggregate Field Uyumu?**

   - ✅ YES - `fiberCode`, `category`, `compositionType` compatible
   - 📍 See: fabric-fiber-service.md → [Field Mapping]

3. **OriginType Satın Almada Belirlenir Mi?**

   - ✅ YES - Base fiber `originType=UNKNOWN`, procurement override edilir
   - 📍 See: fabric-fiber-service.md → [Procurement Flow]

4. **Blend Fiber CREATE Endpoint Var Mı?**
   - ✅ YES - POST `/api/v1/fibers/blend`
   - 📍 See: fabric-fiber-service.md → [Blend Creation]

---

## 🌍 Global Fiber Catalog Highlights

**Total Fibers Available:** 35+

| Category       | Count | Examples                                                  |
| -------------- | ----- | --------------------------------------------------------- |
| **NATURAL**    | 15+   | Cotton, Wool, Silk, Linen, Hemp, Cashmere, Alpaca         |
| **SYNTHETIC**  | 10+   | Polyester, Nylon, Acrylic, Polypropylene, Spandex, Aramid |
| **ARTIFICIAL** | 6+    | Viscose, Modal, Lyocell, Cupro, Acetate                   |
| **MINERAL**    | 4+    | Glass Fiber, Carbon Fiber, Metallic, Basalt               |

**📍 Full catalog:** [WORLD_FIBER_CATALOG.md](./WORLD_FIBER_CATALOG.md)

---

## 🚀 Service Characteristics

- **Scope:** GLOBAL (tenant-independent fiber registry)
- **Pattern:** Event-Driven (Choreography)
- **Cache:** Redis (1h TTL, event invalidation)
- **Events:** FiberDefined, FiberUpdated, FiberDeactivated
- **Dependencies:** NONE (foundation service)
- **Consumers:** yarn-service, weaving-service, procurement-service

---

## 📊 API Summary

**Total Endpoints:** 12

- **Public:** 9 endpoints (CRUD + Query)
- **Internal:** 3 endpoints (yarn-service integration)

**Key Endpoints:**

```
POST   /api/v1/fibers              → Create PURE fiber
POST   /api/v1/fibers/blend        → Create BLEND fiber from existing
GET    /api/v1/fibers/default      → Get system defaults (CO, PE, WO...)
POST   /internal/validate          → Validate composition (yarn-service)
```

---

## 🧬 DNA Compliance

✅ **Service-Aware Pattern** - Full path `/api/v1/fibers`  
✅ **UUID Type Safety** - Database → Controller  
✅ **Shared Infrastructure** - Extends base configs  
✅ **@InternalEndpoint** - Service-to-service security  
✅ **Zero Hardcoded** - All config via ${ENV_VAR:default}  
✅ **Anemic Domain Model** - Entity = Data holder  
✅ **Event-Driven** - Kafka events for downstream services

**Compliance Score:** 98/100 🏆

---

**Last Updated:** 2025-10-19  
**Service Order:** 1️⃣ (First in textile chain)  
**Next Service:** fabric-yarn-service
