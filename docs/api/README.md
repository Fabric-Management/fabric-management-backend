# API Documentation Hub

## 📋 Overview

Bu klasör, Fabric Management System'in tüm API dokümantasyonunu organize eder. Her servis kategorisi için ayrı klasörler ve detaylı API referansları bulunur.

## 📖 API Structure

```
api/
├── README.md                              # Ana API dokümantasyonu
├── core-services/                         # Core servisler API'leri
│   ├── identity-service-api.md
│   ├── user-service-api.md
│   ├── contact-service-api.md
│   └── company-service-api.md
├── hr-services/                           # HR servisler API'leri
│   ├── hr-service-api.md
│   ├── payroll-service-api.md
│   ├── leave-service-api.md
│   └── performance-service-api.md
├── inventory-services/                    # Envanter servisler API'leri
│   ├── inventory-service-api.md
│   ├── catalog-service-api.md
│   ├── pricing-service-api.md
│   ├── procurement-service-api.md
│   └── quality-control-service-api.md
├── order-services/                        # Sipariş servisler API'leri
│   └── order-service-api.md
├── logistics-services/                    # Lojistik servisler API'leri
│   └── logistics-service-api.md
├── production-services/                   # Üretim servisler API'leri
│   └── production-service-api.md
├── financial-services/                    # Mali servisler API'leri
│   ├── accounting-service-api.md
│   ├── invoice-service-api.md
│   ├── payment-service-api.md
│   └── billing-service-api.md
├── ai-analytics-services/                 # AI ve analitik servisler API'leri
│   ├── ai-service-api.md
│   ├── reporting-service-api.md
│   └── notification-service-api.md
└── quality-services/                      # Kalite servisler API'leri
    └── quality-control-service-api.md
```

## 🎯 API Categories

### 🏛️ **Core Services APIs** (4 Services)

- **Identity Service API** (Port: 8081) ✅ - Authentication ve authorization
- **User Service API** (Port: 8082) ✅ - User profile management
- **Contact Service API** (Port: 8083) ✅ - Contact information management
- **Company Service API** (Port: 8084) ❌ - Company management

### 👥 **HR Services APIs** (4 Services)

- **HR Service API** (Port: 8085) ❌ - Human resources management
- **Payroll Service API** (Port: 8086) ❌ - Payroll processing
- **Leave Service API** (Port: 8087) ❌ - Leave management
- **Performance Service API** (Port: 8088) ❌ - Performance management

### 📦 **Inventory Services APIs** (5 Services)

- **Inventory Service API** (Port: 8089) ❌ - Inventory management
- **Catalog Service API** (Port: 8090) ❌ - Product catalog management
- **Pricing Service API** (Port: 8091) ❌ - Pricing management
- **Procurement Service API** (Port: 8092) ❌ - Procurement management
- **Quality Control Service API** (Port: 8093) ❌ - Quality control management

### 📋 **Order Services APIs** (1 Service)

- **Order Service API** (Port: 8094) ❌ - Order management

### 🚚 **Logistics Services APIs** (1 Service)

- **Logistics Service API** (Port: 8095) ❌ - Logistics management

### 🏭 **Production Services APIs** (1 Service)

- **Production Service API** (Port: 8096) ❌ - Production management

### 💰 **Financial Services APIs** (4 Services)

- **Accounting Service API** (Port: 8097) ❌ - Accounting management
- **Invoice Service API** (Port: 8098) ❌ - Invoice management
- **Payment Service API** (Port: 8099) ❌ - Payment processing
- **Billing Service API** (Port: 8100) ❌ - Billing management

### 🤖 **AI & Analytics Services APIs** (3 Services)

- **AI Service API** (Port: 8101) ❌ - AI integration
- **Reporting Service API** (Port: 8102) ❌ - Reporting and analytics
- **Notification Service API** (Port: 8103) ❌ - Notification management

### 🔍 **Quality Services APIs** (1 Service)

- **Quality Control Service API** (Port: 8104) ✅ - Quality control management

## 📊 API Status

### ✅ **Completed APIs** (4/24)

- Identity Service API
- User Service API
- Contact Service API
- Quality Control Service API

### 🚧 **In Progress APIs** (0/24)

- None currently

### 📋 **Planned APIs** (20/24)

- Company Service API
- HR Services APIs (4)
- Inventory Services APIs (5)
- Order Services APIs (1)
- Logistics Services APIs (1)
- Production Services APIs (1)
- Financial Services APIs (4)
- AI & Analytics Services APIs (3)

## 🚀 Quick Navigation

### **Core Services**

- [Identity Service API](core-services/identity-service-api.md) - Authentication endpoints
- [User Service API](core-services/user-service-api.md) - User management endpoints
- [Contact Service API](core-services/contact-service-api.md) - Contact management endpoints
- [Company Service API](core-services/company-service-api.md) - Company management endpoints

### **HR Services**

- [HR Service API](hr-services/hr-service-api.md) - HR management endpoints
- [Payroll Service API](hr-services/payroll-service-api.md) - Payroll processing endpoints
- [Leave Service API](hr-services/leave-service-api.md) - Leave management endpoints
- [Performance Service API](hr-services/performance-service-api.md) - Performance management endpoints

### **Inventory Services**

- [Inventory Service API](inventory-services/inventory-service-api.md) - Inventory management endpoints
- [Catalog Service API](inventory-services/catalog-service-api.md) - Product catalog endpoints
- [Pricing Service API](inventory-services/pricing-service-api.md) - Pricing management endpoints
- [Procurement Service API](inventory-services/procurement-service-api.md) - Procurement management endpoints
- [Quality Control Service API](inventory-services/quality-control-service-api.md) - Quality control endpoints

### **Order Services**

- [Order Service API](order-services/order-service-api.md) - Order management endpoints

### **Logistics Services**

- [Logistics Service API](logistics-services/logistics-service-api.md) - Logistics management endpoints

### **Production Services**

- [Production Service API](production-services/production-service-api.md) - Production management endpoints

### **Financial Services**

- [Accounting Service API](financial-services/accounting-service-api.md) - Accounting management endpoints
- [Invoice Service API](financial-services/invoice-service-api.md) - Invoice management endpoints
- [Payment Service API](financial-services/payment-service-api.md) - Payment processing endpoints
- [Billing Service API](financial-services/billing-service-api.md) - Billing management endpoints

### **AI & Analytics Services**

- [AI Service API](ai-analytics-services/ai-service-api.md) - AI integration endpoints
- [Reporting Service API](ai-analytics-services/reporting-service-api.md) - Reporting endpoints
- [Notification Service API](ai-analytics-services/notification-service-api.md) - Notification endpoints

### **Quality Services**

- [Quality Control Service API](quality-services/quality-control-service-api.md) - Quality control endpoints

## 🎯 Next Steps

### **Phase 1: Complete Core Services APIs** (Priority: High)

1. Company Service API

### **Phase 2: HR Services APIs** (Priority: High)

1. HR Service API
2. Payroll Service API
3. Leave Service API
4. Performance Service API

### **Phase 3: Inventory Services APIs** (Priority: High)

1. Inventory Service API
2. Catalog Service API
3. Pricing Service API
4. Procurement Service API
5. Quality Control Service API

### **Phase 4: Order & Logistics Services APIs** (Priority: High)

1. Order Service API
2. Logistics Service API

### **Phase 5: Production Services APIs** (Priority: High)

1. Production Service API

### **Phase 6: Financial Services APIs** (Priority: High)

1. Accounting Service API
2. Invoice Service API
3. Payment Service API
4. Billing Service API

### **Phase 7: AI & Analytics Services APIs** (Priority: Medium)

1. AI Service API
2. Reporting Service API
3. Notification Service API

## 📈 API Documentation Standards

### **Required Sections**

- **Overview**: Service purpose ve responsibilities
- **Authentication**: Authentication requirements
- **Endpoints**: All API endpoints with examples
- **Request/Response Models**: Data models ve examples
- **Error Handling**: Error codes ve messages
- **Rate Limiting**: Rate limiting information
- **Examples**: Complete request/response examples

### **Optional Sections**

- **SDKs**: Client SDKs
- **Postman Collection**: Postman collection links
- **OpenAPI Spec**: OpenAPI specification
- **Changelog**: API version changes

---

**Last Updated**: 2024-01-XX  
**Version**: 1.0.0  
**Maintainer**: API Team
