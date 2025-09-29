# 📚 Fabric Management System - Documentation Hub

## 🎯 Overview

Bu dokümantasyon hub'ı, Fabric Management System'in tüm teknik dokümantasyonunu merkezi bir yerden erişilebilir hale getirir. Sistem mimarisi, servis detayları, entegrasyon rehberleri ve geliştirme kılavuzları burada organize edilmiştir.

## 🏗️ Documentation Architecture

```
docs/
├── 📋 README.md                           # Ana dokümantasyon hub'ı (bu dosya)
├── 🏛️ architecture/                      # Sistem mimarisi ve tasarım kararları
│   ├── README.md                          # Mimari genel bakış
│   ├── MICROSERVICE_ARCHITECTURE_OVERVIEW.md # Tüm microservice'lerin genel bakışı
│   ├── CORE_SERVICES/                     # Core servisler dokümantasyonu
│   │   ├── IDENTITY_SERVICE_ARCHITECTURE.md
│   │   ├── USER_SERVICE_ARCHITECTURE.md
│   │   ├── CONTACT_SERVICE_ARCHITECTURE.md
│   │   └── COMPANY_SERVICE_ARCHITECTURE.md
│   ├── HR_SERVICES/                       # HR yönetim servisleri
│   │   ├── HR_SERVICE_ARCHITECTURE.md
│   │   ├── PAYROLL_SERVICE_ARCHITECTURE.md
│   │   ├── LEAVE_SERVICE_ARCHITECTURE.md
│   │   └── PERFORMANCE_SERVICE_ARCHITECTURE.md
│   ├── INVENTORY_SERVICES/                # Envanter yönetim servisleri
│   │   ├── WAREHOUSE_SERVICE_ARCHITECTURE.md
│   │   ├── STOCK_SERVICE_ARCHITECTURE.md
│   │   ├── FABRIC_SERVICE_ARCHITECTURE.md
│   │   └── PROCUREMENT_SERVICE_ARCHITECTURE.md
│   ├── FINANCIAL_SERVICES/               # Mali servisler
│   │   ├── ACCOUNTING_SERVICE_ARCHITECTURE.md
│   │   ├── INVOICE_SERVICE_ARCHITECTURE.md
│   │   ├── PAYMENT_SERVICE_ARCHITECTURE.md
│   │   └── BILLING_SERVICE_ARCHITECTURE.md
│   ├── AI_ANALYTICS_SERVICES/            # AI ve analitik servisler
│   │   ├── AI_SERVICE_ARCHITECTURE.md
│   │   ├── REPORTING_SERVICE_ARCHITECTURE.md
│   │   └── NOTIFICATION_SERVICE_ARCHITECTURE.md
│   └── QUALITY_SERVICES/                 # Kalite yönetim servisleri
│       └── QUALITY_CONTROL_SERVICE_ARCHITECTURE.md
├── 🔗 integration/                       # Servis entegrasyon rehberleri
│   ├── README.md                          # Entegrasyon genel bakışı
│   ├── identity-user-integration.md       # Identity-User entegrasyonu
│   ├── service-communication.md           # Servisler arası iletişim
│   ├── event-driven-architecture.md      # Event-driven mimari
│   └── data-consistency.md               # Veri tutarlılığı stratejileri
├── 🛠️ development/                       # Geliştirme rehberleri
│   ├── README.md                          # Geliştirme genel bakışı
│   ├── getting-started/                  # Başlangıç rehberleri
│   │   ├── README.md
│   │   ├── environment-setup.md
│   │   ├── local-development.md
│   │   └── debugging-guide.md
│   ├── project-structure.md              # Proje yapısı
│   ├── coding-standards.md               # Kodlama standartları
│   ├── testing-guide.md                  # Test rehberi
│   └── deployment-guide.md                # Deployment rehberi
├── 🚀 deployment/                        # Deployment dokümantasyonu
│   ├── README.md                          # Deployment genel bakışı
│   ├── docker-setup.md                    # Docker kurulumu
│   ├── kubernetes-deployment.md           # Kubernetes deployment
│   ├── production-setup.md                # Production kurulumu
│   └── monitoring-setup.md               # Monitoring kurulumu
├── 📖 api/                               # API dokümantasyonu
│   ├── README.md                          # API genel bakışı
│   ├── core-services/                    # Core servisler API'leri
│   │   ├── identity-service-api.md
│   │   ├── user-service-api.md
│   │   ├── contact-service-api.md
│   │   └── company-service-api.md
│   ├── hr-services/                      # HR servisler API'leri
│   │   ├── hr-service-api.md
│   │   ├── payroll-service-api.md
│   │   ├── leave-service-api.md
│   │   └── performance-service-api.md
│   ├── inventory-services/               # Envanter servisler API'leri
│   │   ├── warehouse-service-api.md
│   │   ├── stock-service-api.md
│   │   ├── fabric-service-api.md
│   │   └── procurement-service-api.md
│   ├── financial-services/               # Mali servisler API'leri
│   │   ├── accounting-service-api.md
│   │   ├── invoice-service-api.md
│   │   ├── payment-service-api.md
│   │   └── billing-service-api.md
│   ├── ai-analytics-services/            # AI ve analitik servisler API'leri
│   │   ├── ai-service-api.md
│   │   ├── reporting-service-api.md
│   │   └── notification-service-api.md
│   └── quality-services/                 # Kalite servisler API'leri
│       └── quality-control-service-api.md
└── 🧪 testing/                           # Test dokümantasyonu
    ├── README.md                          # Test genel bakışı
    ├── unit-testing.md                    # Unit test rehberi
    ├── integration-testing.md             # Integration test rehberi
    ├── e2e-testing.md                     # End-to-end test rehberi
    ├── performance-testing.md             # Performance test rehberi
    └── UUID_MIGRATION_GUIDE.md            # UUID migration rehberi
```

## 🎯 Documentation Categories

### 🏛️ **Architecture Documentation**

Sistem mimarisi, tasarım kararları ve microservice yapısı hakkında detaylı bilgiler.

**Ana Dosyalar:**

- [Architecture Overview](architecture/README.md) - Genel mimari bakış
- [Microservice Architecture Overview](architecture/MICROSERVICE_ARCHITECTURE_OVERVIEW.md) - Tüm servislerin genel bakışı

**Servis Kategorileri:**

- **Core Services**: Identity, User, Contact, Company
- **HR Services**: HR, Payroll, Leave, Performance
- **Inventory Services**: Warehouse, Stock, Fabric, Procurement
- **Financial Services**: Accounting, Invoice, Payment, Billing
- **AI & Analytics Services**: AI, Reporting, Notification
- **Quality Services**: Quality Control

### 🔗 **Integration Documentation**

Servisler arası entegrasyon, iletişim ve veri akışı rehberleri.

**Ana Konular:**

- Servisler arası iletişim
- Event-driven architecture
- Veri tutarlılığı stratejileri
- API entegrasyonları

### 🛠️ **Development Documentation**

Geliştiriciler için rehberler, standartlar ve best practices.

**Ana Konular:**

- Environment setup
- Local development
- Coding standards
- Testing strategies
- Debugging techniques

### 🚀 **Deployment Documentation**

Production deployment, infrastructure ve monitoring rehberleri.

**Ana Konular:**

- Docker setup
- Kubernetes deployment
- Production configuration
- Monitoring ve logging

### 📖 **API Documentation**

Tüm servislerin API dokümantasyonu ve kullanım örnekleri.

**API Kategorileri:**

- Core Services APIs
- HR Services APIs
- Inventory Services APIs
- Financial Services APIs
- AI & Analytics Services APIs
- Quality Services APIs

### 🧪 **Testing Documentation**

Test stratejileri, rehberleri ve best practices.

**Test Türleri:**

- Unit Testing
- Integration Testing
- End-to-End Testing
- Performance Testing

## 🚀 Quick Start Guide

### **Yeni Geliştirici İçin:**

1. [Getting Started](development/getting-started/README.md) - Temel kurulum
2. [Project Structure](development/project-structure.md) - Proje yapısı
3. [Architecture Overview](architecture/README.md) - Sistem mimarisi
4. [API Documentation](api/README.md) - API referansları

### **Mimari Anlayış İçin:**

1. [Microservice Architecture Overview](architecture/MICROSERVICE_ARCHITECTURE_OVERVIEW.md) - Genel bakış
2. [Service Integration](integration/README.md) - Servis entegrasyonları
3. [Core Services](architecture/CORE_SERVICES/) - Temel servisler
4. [Event-Driven Architecture](integration/event-driven-architecture.md) - Event mimarisi

### **Deployment İçin:**

1. [Deployment Overview](deployment/README.md) - Deployment genel bakışı
2. [Docker Setup](deployment/docker-setup.md) - Docker kurulumu
3. [Kubernetes Deployment](deployment/kubernetes-deployment.md) - K8s deployment
4. [Production Setup](deployment/production-setup.md) - Production kurulumu

## 📊 Documentation Status

### ✅ **Completed Documentation**

- Architecture Overview
- Microservice Architecture Overview
- Identity Service Architecture
- User Service Architecture
- Contact Service Architecture
- Quality Control Service Architecture
- Identity-User Integration Guide

### 🚧 **In Progress Documentation**

- Company Service Architecture
- HR Services Architecture
- Inventory Services Architecture
- Financial Services Architecture
- AI & Analytics Services Architecture

### 📋 **Planned Documentation**

- Complete API Documentation
- Comprehensive Testing Guide
- Production Deployment Guide
- Monitoring & Observability Guide
- Performance Optimization Guide

## 🔄 Documentation Maintenance

### **Update Schedule**

- **Weekly**: API documentation updates
- **Bi-weekly**: Architecture documentation reviews
- **Monthly**: Complete documentation audit
- **Quarterly**: Documentation structure review

### **Contributing Guidelines**

1. Follow the established structure
2. Use consistent formatting
3. Include code examples
4. Add diagrams where helpful
5. Keep documentation up-to-date
6. Review and test all examples

## 📞 Support & Contact

- **Architecture Questions**: Architecture team
- **Development Issues**: Development team
- **Deployment Issues**: DevOps team
- **Documentation Issues**: Technical writing team

---

**Last Updated**: 2024-01-XX  
**Version**: 1.0.0  
**Maintainer**: Fabric Management System Team
