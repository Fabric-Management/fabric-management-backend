# 📦 Service Documentation

**Last Updated:** October 10, 2025  
**Purpose:** Service-specific architecture and implementation guides  
**Status:** ✅ Active

---

## 📚 Available Services

| Service             | Documentation                              | Quick README                                             | Port | Status        |
| ------------------- | ------------------------------------------ | -------------------------------------------------------- | ---- | ------------- |
| **User Service**    | [user-service.md](./user-service.md)       | [Quick README](../../services/user-service/README.md)    | 8081 | ✅ Production |
| **Company Service** | [company-service.md](./company-service.md) | [Quick README](../../services/company-service/README.md) | 8083 | ✅ Production |
| **Contact Service** | [contact-service.md](./contact-service.md) | [Quick README](../../services/contact-service/README.md) | 8082 | ✅ Production |
| **API Gateway**     | [api-gateway.md](./api-gateway.md)         | [Quick README](../../services/api-gateway/README.md)     | 8080 | ✅ Production |

---

## 🎯 Documentation Types

### Service READMEs (`/services/{service}/README.md`)

**Purpose:** Quick reference for developers working on that service

- ⚡ Quick start guide
- 🔑 Key features (bullet points)
- ⚙️ Basic configuration
- 🐛 Common troubleshooting
- 📖 Links to detailed docs

**Format:** Lightweight (~80-100 lines)

### Service Documentation (`/docs/services/{service}.md`)

**Purpose:** Comprehensive service architecture and implementation

- 🏗️ Complete architecture
- 📐 Domain model details
- 🔄 Integration patterns
- 🧪 Testing strategies
- 📊 API specifications
- 🔐 Security implementation

**Format:** Detailed (300-800 lines)

---

## 📖 Quick Navigation

### By Topic

| Need                                 | Check                                 |
| ------------------------------------ | ------------------------------------- |
| **Quick start a service?**           | `/services/{service}/README.md`       |
| **Understand service architecture?** | `/docs/services/{service}.md`         |
| **See all services overview?**       | [ARCHITECTURE.md](../ARCHITECTURE.md) |
| **API endpoints?**                   | [api/README.md](../api/README.md)     |

### By Service

| Service     | Quick Start                                          | Full Docs                       | Architecture Pattern      |
| ----------- | ---------------------------------------------------- | ------------------------------- | ------------------------- |
| **User**    | 👉 [Quick](../../services/user-service/README.md)    | 📖 [Full](./user-service.md)    | Clean Architecture + DDD  |
| **Company** | 👉 [Quick](../../services/company-service/README.md) | 📖 [Full](./company-service.md) | Clean Architecture + CQRS |
| **Contact** | 👉 [Quick](../../services/contact-service/README.md) | 📖 [Full](./contact-service.md) | Clean Architecture        |
| **Gateway** | 👉 [Quick](../../services/api-gateway/README.md)     | 📖 [Full](./api-gateway.md)     | Spring Cloud Gateway      |

---

## 🔗 Related Documentation

- [System Architecture](../ARCHITECTURE.md) - Overall system design
- [API Documentation](../api/README.md) - REST API specs
- [Development Guide](../development/README.md) - Development standards
- [Deployment Guide](../deployment/README.md) - Deployment instructions

---

**Maintained By:** Backend Team  
**Last Updated:** 2025-10-10  
**Status:** ✅ Active
