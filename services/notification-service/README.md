# 📧 Notification Service

**Version:** 1.0.0  
**Port:** 8084  
**Purpose:** Multi-tenant notification service (Email/SMS/WhatsApp)

---

## 📋 Fihrist (Index)

| Konu             | Açıklama                                                              |
| ---------------- | --------------------------------------------------------------------- |
| **Architecture** | Event-driven, multi-channel, tenant-specific with platform fallback   |
| **Channels**     | Email (SMTP), SMS (future), WhatsApp (prioritized)                    |
| **Key Features** | Tenant configs, verification codes, template-based, delivery tracking |
| **Tech Stack**   | Spring Boot, Kafka, PostgreSQL, JavaMail, Redis                       |

---

## 🎯 Business Context

### Purpose

Tenant'ların kendi email ve telefon altyapılarını kullanarak müşterilerine bildirim göndermesini sağlar.

### Key Features

1. **Multi-Channel:** Email, SMS, WhatsApp
2. **Tenant-Specific:** Her tenant kendi SMTP/SMS/WhatsApp credentials'larını tanımlar
3. **Platform Fallback:** Tenant config yoksa platform config kullanılır
4. **Cost Optimization:** WhatsApp SMS'ten öncelikli (daha ucuz)
5. **Event-Driven:** Kafka ile asenkron bildirim gönderimi
6. **Verification Codes:** 6 haneli kod, 15 dakika geçerli, 5 deneme hakkı

---

## 🏗️ Architecture

### Event Flow

```
User Service → Kafka (UserCreatedEvent)
              ↓
Notification Service (Listener)
              ↓
Get Tenant Config (or Platform Fallback)
              ↓
Send via WhatsApp → Fallback to Email
```

### Tenant Configuration Pattern

```
1. Check tenant_id → notification_configs table
2. If exists → Use tenant SMTP/WhatsApp credentials
3. If NOT exists → Use platform fallback (info@storeandsale.shop)
```

---

## 📁 Structure

```
notification-service/
├── api/                        # REST endpoints (tenant config CRUD)
│   ├── NotificationConfigController.java
│   └── dto/
├── application/
│   ├── mapper/                 # DTO ↔ Entity mapping
│   └── service/                # Business logic
│       ├── NotificationConfigService.java
│       └── NotificationDispatchService.java
├── domain/
│   ├── aggregate/
│   │   └── NotificationConfig.java  # Tenant SMTP/SMS/WhatsApp config
│   ├── entity/
│   │   └── NotificationLog.java     # Delivery tracking
│   ├── event/                        # Kafka events consumed
│   └── valueobject/
│       ├── NotificationChannel.java (EMAIL, SMS, WHATSAPP)
│       └── NotificationProvider.java (SMTP, TWILIO, etc.)
└── infrastructure/
    ├── config/
    │   ├── EmailConfig.java          # SMTP configuration
    │   └── PlatformFallbackConfig.java
    ├── messaging/
    │   ├── UserEventListener.java    # Kafka consumer
    │   └── ContactEventListener.java
    ├── notification/
    │   ├── EmailNotificationService.java
    │   ├── WhatsAppNotificationService.java
    │   └── SmsNotificationService.java
    └── repository/
        ├── NotificationConfigRepository.java
        └── NotificationLogRepository.java
```

---

## 🗄️ Database Schema

### notification_configs

```sql
CREATE TABLE notification_configs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,           -- EMAIL, SMS, WHATSAPP
    provider VARCHAR(50) NOT NULL,          -- SMTP, TWILIO, etc.
    is_enabled BOOLEAN DEFAULT true,

    -- SMTP (Email)
    smtp_host VARCHAR(255),
    smtp_port INTEGER,
    smtp_username VARCHAR(255),
    smtp_password VARCHAR(500),             -- Encrypted
    from_email VARCHAR(255),
    from_name VARCHAR(255),

    -- SMS/WhatsApp
    api_key VARCHAR(500),                   -- Encrypted
    from_number VARCHAR(50),

    -- Metadata
    priority INTEGER DEFAULT 0,             -- 0=highest (WhatsApp), 1=Email, 2=SMS
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,

    UNIQUE(tenant_id, channel, deleted_at)
);
```

### notification_logs

```sql
CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    body TEXT,
    status VARCHAR(50) NOT NULL,            -- PENDING, SENT, FAILED
    error_message TEXT,
    attempts INTEGER DEFAULT 0,
    sent_at TIMESTAMP,
    created_at TIMESTAMP
);
```

---

## 🔧 Configuration

### Platform Fallback (Environment Variables)

```yaml
PLATFORM_EMAIL_FROM: info@storeandsale.shop
PLATFORM_EMAIL_FROM_NAME: Store and Sale
PLATFORM_WHATSAPP_FROM_NUMBER: +447553838399
PLATFORM_SMS_FROM_NUMBER: +447553838399
```

### Tenant-Specific (Database)

```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "channel": "EMAIL",
  "provider": "SMTP",
  "smtpHost": "smtp.acmetekstil.com",
  "smtpPort": 587,
  "smtpUsername": "noreply@acmetekstil.com",
  "smtpPassword": "encrypted_password",
  "fromEmail": "noreply@acmetekstil.com",
  "fromName": "Acme Tekstil"
}
```

---

## 🚀 API Endpoints

### Tenant Config Management

```
POST   /api/v1/notifications/config          # Create tenant config
GET    /api/v1/notifications/config          # List tenant configs
PUT    /api/v1/notifications/config/{id}     # Update config
DELETE /api/v1/notifications/config/{id}     # Soft delete
```

### Internal Endpoints (Kafka-triggered)

- No direct HTTP endpoints for sending notifications
- All triggered via Kafka events

---

## 📊 Kafka Topics

### Consumed Topics

```yaml
user.created: # New user registered → send verification email
user.password.reset: # Password reset → send reset code
contact.verification: # Contact verification → send code
```

### Event Schema (Example)

```json
{
  "eventId": "uuid",
  "eventType": "USER_CREATED",
  "tenantId": "uuid",
  "userId": "uuid",
  "email": "user@example.com",
  "verificationCode": "123456",
  "timestamp": "2025-10-15T10:00:00Z"
}
```

---

## 🔐 Security

### Credential Encryption

- SMTP passwords and API keys encrypted in database
- Spring Jasypt or AWS KMS integration

### Authentication

- Internal API Key for service-to-service calls
- JWT authentication for tenant config management

---

## 📈 Monitoring

### Health Check

```
GET /actuator/health
```

### Metrics

```
GET /actuator/prometheus

# Custom metrics:
notification_sent_total{channel="email", status="success"}
notification_sent_total{channel="whatsapp", status="failed"}
notification_retry_total{channel="email"}
```

---

## 🧪 Testing

### Local Testing (SMTP)

```bash
# Use Gmail SMTP (app password required)
PLATFORM_SMTP_HOST=smtp.gmail.com
PLATFORM_SMTP_PORT=587
PLATFORM_SMTP_USERNAME=your-email@gmail.com
PLATFORM_SMTP_PASSWORD=your-app-password
```

---

## 📝 Notes

- **WhatsApp prioritized:** Cheaper than SMS, fallback to Email if not configured
- **Platform fallback:** Always available (info@storeandsale.shop)
- **No hardcoded credentials:** All environment-driven
- **Kafka-driven:** No synchronous HTTP calls from other services
- **Idempotent:** Uses event IDs to prevent duplicate sends

---

**Last Updated:** 2025-10-15  
**Author:** Fabric Management Team
