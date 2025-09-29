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
│   ├── warehouse-service-api.md
│   ├── stock-service-api.md
│   ├── fabric-service-api.md
│   └── procurement-service-api.md
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

### 📦 **Inventory Services APIs** (4 Services)

- **Warehouse Service API** (Port: 8089) ❌ - Warehouse management
- **Stock Service API** (Port: 8090) ❌ - Stock management
- **Fabric Service API** (Port: 8091) ❌ - Fabric management
- **Procurement Service API** (Port: 8092) ❌ - Procurement management

### 💰 **Financial Services APIs** (4 Services)

- **Accounting Service API** (Port: 8093) ❌ - Accounting management
- **Invoice Service API** (Port: 8094) ❌ - Invoice management
- **Payment Service API** (Port: 8095) ❌ - Payment processing
- **Billing Service API** (Port: 8096) ❌ - Billing management

### 🤖 **AI & Analytics Services APIs** (3 Services)

- **AI Service API** (Port: 8097) ❌ - AI integration
- **Reporting Service API** (Port: 8098) ❌ - Reporting and analytics
- **Notification Service API** (Port: 8099) ❌ - Notification management

### 🔍 **Quality Services APIs** (1 Service)

- **Quality Control Service API** (Port: 8100) ✅ - Quality control management

## 📊 API Status

### ✅ **Completed APIs** (4/20)

- Identity Service API
- User Service API
- Contact Service API
- Quality Control Service API

### 🚧 **In Progress APIs** (0/20)

- None currently

### 📋 **Planned APIs** (16/20)

- Company Service API
- HR Services APIs (4)
- Inventory Services APIs (4)
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

- [Warehouse Service API](inventory-services/warehouse-service-api.md) - Warehouse management endpoints
- [Stock Service API](inventory-services/stock-service-api.md) - Stock management endpoints
- [Fabric Service API](inventory-services/fabric-service-api.md) - Fabric management endpoints
- [Procurement Service API](inventory-services/procurement-service-api.md) - Procurement management endpoints

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

1. Warehouse Service API
2. Stock Service API
3. Fabric Service API
4. Procurement Service API

### **Phase 4: Financial Services APIs** (Priority: High)

1. Accounting Service API
2. Invoice Service API
3. Payment Service API
4. Billing Service API

### **Phase 5: AI & Analytics Services APIs** (Priority: Medium)

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
