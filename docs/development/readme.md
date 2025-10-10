# 📖 Development Documentation

**Last Updated:** October 10, 2025  
**Purpose:** Complete development guide and standards for Fabric Management System  
**Status:** ✅ Active

---

## 📚 Documentation Index

### 🎯 Getting Started

| Document | Description | Priority | Time |
|----------|-------------|----------|------|
| [GETTING_STARTED.md](./GETTING_STARTED.md) | ⭐ **Complete onboarding** - Quick start, local dev, hot reload | 🔴 High | 25 min |

### 📐 Core Standards & Principles

| Document | Description | Priority | Time |
|----------|-------------|----------|------|
| [PRINCIPLES.md](./PRINCIPLES.md) | ⭐ **Core principles: SOLID, DRY, KISS, YAGNI, Loose Coupling, NO USERNAME** | 🔴 **CRITICAL** | 45 min |
| [CODE_STRUCTURE_GUIDE.md](./CODE_STRUCTURE_GUIDE.md) | Where to write code - file organization | 🟡 Medium | 20 min |
| [CODE_MIGRATION_GUIDE.md](./CODE_MIGRATION_GUIDE.md) | Before/after patterns, refactoring examples | 🟢 Low | 20 min |

### 🌐 API & Data Standards

| Document | Description | Priority | Time |
|----------|-------------|----------|------|
| [MICROSERVICES_API_STANDARDS.md](./MICROSERVICES_API_STANDARDS.md) | ⭐ **API Gateway routing, controller patterns** | 🔴 **MANDATORY** | 35 min |
| [DATA_TYPES_STANDARDS.md](./DATA_TYPES_STANDARDS.md) | ⭐ **UUID usage standards - 100% compliance required** | 🔴 **MANDATORY** | 30 min |

### 🔐 Security & Authorization

| Document | Description | Priority | Time |
|----------|-------------|----------|------|
| [POLICY_AUTHORIZATION.md](./POLICY_AUTHORIZATION.md) | ⭐ **Policy authorization index** | 🟡 Medium | 5 min |
| ├─ [POLICY_AUTHORIZATION_PRINCIPLES.md](./POLICY_AUTHORIZATION_PRINCIPLES.md) | Detailed principles & architecture | 🟡 Medium | 40 min |
| └─ [POLICY_AUTHORIZATION_QUICK_START.md](./POLICY_AUTHORIZATION_QUICK_START.md) | Quick implementation guide | 🟢 Low | 20 min |

---

## 🎓 Learning Paths

### 🆕 New Developers (First Week)

```
Day 1:
├─ QUICK_START.md (15 min)
├─ LOCAL_DEVELOPMENT_GUIDE.md (30 min)
└─ Setup local environment

Day 2-3:
├─ PRINCIPLES.md (45 min) ⚠️ MANDATORY
├─ CODE_STRUCTURE_GUIDE.md (20 min)
└─ Write first endpoint

Day 4-5:
├─ MICROSERVICES_API_STANDARDS.md (35 min) ⚠️ MANDATORY
├─ DATA_TYPES_STANDARDS.md (30 min) ⚠️ MANDATORY
├─ POLICY_AUTHORIZATION.md (5 min) - Overview
└─ Code review & feedback
```

### 👨‍💻 Experienced Developers (First Day)

```
Priority Reading (2 hours):
├─ PRINCIPLES.md (45 min) - Understand NO USERNAME, SOLID, Loose Coupling
├─ DATA_TYPES_STANDARDS.md (30 min) - UUID compliance is mandatory
├─ MICROSERVICES_API_STANDARDS.md (35 min) - API Gateway patterns
└─ Start coding with team standards
```

### 🏗️ Architects & Tech Leads

```
Complete Review (4 hours):
├─ Read all documents in order
├─ Understand architectural decisions
├─ Review PRINCIPLES.md Loose Coupling section
└─ Guide team on standards
```

---

## ⚠️ Critical Standards (MUST READ)

### 🚨 Mandatory Compliance

Before writing any code, you MUST read and understand:

1. **[PRINCIPLES.md](./PRINCIPLES.md)** - Core principles including:
   - ⛔ **NO USERNAME PRINCIPLE** - System uses `contactValue` (email/phone) for auth
   - 🏗️ **SOLID Principles** - Code quality standards
   - 🔗 **Loose Coupling** - Microservice architecture patterns
   - 🚫 **Anti-Patterns** - What NOT to do

2. **[DATA_TYPES_STANDARDS.md](./DATA_TYPES_STANDARDS.md)** - UUID Standards:
   - ⚠️ All IDs MUST be UUID type throughout the stack
   - ❌ No String IDs in business logic
   - ✅ Only convert to String at system boundaries
   - 📊 100% compliance required - Non-compliance rejected in code review

3. **[MICROSERVICES_API_STANDARDS.md](./MICROSERVICES_API_STANDARDS.md)** - API Patterns:
   - 🎯 Service-Aware Pattern (full paths: `/api/v1/users`)
   - 🚪 API Gateway routing rules
   - 🔒 Authentication & authorization patterns
   - 📝 Request/response standards

---

## 📋 Quick Reference

### 🤔 "How Do I...?"

| Question | Document | Section |
|----------|----------|---------|
| Set up local environment? | LOCAL_DEVELOPMENT_GUIDE.md | Setup |
| Where do I write code? | CODE_STRUCTURE_GUIDE.md | File Organization |
| How do I use UUIDs? | DATA_TYPES_STANDARDS.md | UUID Implementation |
| What are the coding principles? | PRINCIPLES.md | SOLID, DRY, KISS |
| How do I structure APIs? | MICROSERVICES_API_STANDARDS.md | Controller Patterns |
| How do I implement loose coupling? | PRINCIPLES.md | Loose Coupling Principles |
| Why no username field? | PRINCIPLES.md | NO USERNAME PRINCIPLE |
| How do I test my code? | DEVELOPER_GUIDE.md | Testing Strategy |
| What are the git workflows? | DEVELOPER_GUIDE.md | Development Workflow |

### 🔍 Common Tasks

| Task | Steps | Time |
|------|-------|------|
| **Create new endpoint** | 1. Read MICROSERVICES_API_STANDARDS.md<br/>2. Follow Controller Pattern<br/>3. Use UUID for IDs<br/>4. Write tests | 30 min |
| **Add new entity** | 1. Read CODE_STRUCTURE_GUIDE.md<br/>2. Create in `/domain/entity/`<br/>3. Use UUID for ID<br/>4. Implement Builder pattern | 20 min |
| **Implement new service** | 1. Read PRINCIPLES.md<br/>2. Follow SOLID principles<br/>3. Use Loose Coupling patterns<br/>4. Add comprehensive tests | 60 min |

---

## ✅ Code Review Checklist

Before submitting PR, verify:

### 🏗️ Architecture & Design
- [ ] Follows SOLID principles
- [ ] Implements Loose Coupling patterns
- [ ] No tight coupling between services
- [ ] Clean separation of concerns

### 🆔 Data Types
- [ ] All IDs are UUID type (not String)
- [ ] UUID used throughout internal stack
- [ ] String conversion only at boundaries
- [ ] No manual UUID manipulation

### 🚫 NO USERNAME Compliance
- [ ] No `username` field in entities
- [ ] Uses `contactValue` for authentication
- [ ] JWT contains `userId` (UUID), not username
- [ ] No username in method parameters

### 🌐 API Standards
- [ ] Uses full paths (`/api/v1/users`)
- [ ] Follows Service-Aware Pattern
- [ ] Proper request/response DTOs
- [ ] Pagination implemented for lists

### 🧪 Testing
- [ ] Unit tests written (80%+ coverage)
- [ ] Domain logic tests (100% coverage)
- [ ] Integration tests for critical paths
- [ ] All tests passing

### 📝 Documentation
- [ ] Code is self-documenting
- [ ] Complex logic has comments explaining "why"
- [ ] API endpoints documented
- [ ] README updated if needed

---

## 🔗 Related Documentation

### Internal Links
- [Architecture Documentation](../architecture/) - System architecture
- [API Documentation](../api/) - REST API specs
- [Deployment Guide](../deployment/) - Deployment instructions
- [Security Documentation](../SECURITY.md) - Security practices

### Core Documents
- [AI Assistant Learnings](../AI_ASSISTANT_LEARNINGS.md) - AI behavior guidelines
- [Main README](../README.md) - Project overview
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Detailed architecture

---

## 📞 Support & Help

### Getting Help
- **Slack**: #fabric-dev (general), #fabric-backend (backend-specific)
- **Documentation Issues**: Open GitHub issue with `docs` label
- **Code Questions**: Ask in daily standup or #fabric-dev

### Office Hours
- **Technical Lead**: Tuesday & Thursday, 2-4 PM
- **Architecture Review**: Wednesday, 10 AM - 12 PM

### Contributing to Docs
1. Read document you want to update
2. Make changes following existing format
3. Update "Last Updated" timestamp
4. Create PR with clear description
5. Tag documentation maintainer for review

---

**Prepared By:** Backend Team  
**Last Updated:** 2025-10-10 (Major cleanup & reorganization)  
**Version:** 2.0  
**Status:** ✅ Active - All standards enforced

