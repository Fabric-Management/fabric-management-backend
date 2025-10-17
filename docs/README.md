# 📚 Fabric Management - Architecture & Standards

**Version:** 4.0 - Clean Architecture Focus  
**Last Updated:** October 16, 2025  
**Purpose:** Core architecture documentation & development standards

---

## 🎯 Quick Start

| Role             | Document                                                 | Description                         |
| ---------------- | -------------------------------------------------------- | ----------------------------------- |
| **🤖 AI**        | [AI_ASSISTANT_LEARNINGS.md](./AI_ASSISTANT_LEARNINGS.md) | AI behavior & project philosophy    |
| **💎 Developer** | [DEVELOPER_PROTOCOL.md](./DEVELOPER_PROTOCOL.md)         | Developer DNA manifesto (mandatory) |
| **🏗️ Architect** | [ARCHITECTURE.md](./ARCHITECTURE.md)                     | System architecture                 |

---

## 📂 Documentation Structure

```
docs/
├── 💎 DEVELOPER_PROTOCOL.md              # Developer kişiliği, ahlakı, mantığı
├── 🤖 AI_ASSISTANT_LEARNINGS.md          # AI coding principles
├── 🏗️ ARCHITECTURE.md                    # System architecture
├── 🔐 SECURITY.md                        # Security architecture
├── 📖 DOCUMENTATION_PRINCIPLES.md        # Doc standards
│
├── 📐 development/                       # Development standards
│   ├── principles.md                     # SOLID, DRY, NO USERNAME
│   ├── code_structure_guide.md           # Code organization
│   ├── ORCHESTRATION_PATTERN.md          # Atomic operations
│   ├── INTERNAL_ENDPOINT_PATTERN.md      # @InternalEndpoint pattern
│   ├── data_types_standards.md           # UUID standards
│   └── microservices_api_standards.md    # API standards
│
├── 🏛️ architecture/                      # Architecture patterns
│   └── TENANT_MODEL_AND_ROLES_GUIDE.md   # Multi-tenancy model
│
└── 🚀 deployment/                        # Deployment patterns
    ├── ENVIRONMENT_VARIABLES.md          # Config pattern
    └── DATABASE_MIGRATION_STRATEGY.md    # DB migration pattern
```

---

## 🔴 CRITICAL - Must Read

### Developer Onboarding (60 min)

1. **DEVELOPER_PROTOCOL.md** (20 min) - Developer DNA
2. **development/principles.md** (30 min) - SOLID, DRY, NO USERNAME
3. **development/data_types_standards.md** (10 min) - UUID mandatory

### Architecture Understanding (90 min)

1. **ARCHITECTURE.md** (45 min) - System overview
2. **development/ORCHESTRATION_PATTERN.md** (20 min) - Atomic operations
3. **development/INTERNAL_ENDPOINT_PATTERN.md** (15 min) - @InternalEndpoint
4. **architecture/TENANT_MODEL_AND_ROLES_GUIDE.md** (10 min) - Multi-tenancy

---

## ⚡ Sacred Rules

1. **ZERO HARDCODED** - Everything via ${ENV_VAR:default}
2. **PRODUCTION-READY** - No shortcuts, no temporary solutions
3. **HYBRID PATTERN** - Orchestration (core) + Choreography (side effects) + Parallel (validations)
4. **@InternalEndpoint** - Annotation over hardcoded paths
5. **EXTEND SHARED** - Base configs, zero boilerplate
6. **NO USERNAME** - contactValue (email/phone) for auth
7. **UUID TYPE SAFETY** - UUID everywhere internally
8. **ASYNC KAFKA** - CompletableFuture mandatory
9. **CHECK EXISTING** - Before creating new
10. **MINIMAL COMMENTS** - Self-documenting code

---

**Manifesto:**  
Production-ready or nothing. Enterprise-grade or nothing. Excellence or nothing.

**Why?**  
This project is our everything. This code is our legacy. This system is our craftsmanship.
