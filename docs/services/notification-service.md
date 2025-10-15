# 📧 Notification Service Documentation

**Version:** 1.0.0  
**Port:** 8084  
**Database:** notification_db  
**Last Updated:** 2025-10-15

---

## 📋 Fihrist (Index)

| Section               | Description                                        |
| --------------------- | -------------------------------------------------- |
| **Overview**          | Multi-tenant notification service architecture     |
| **Channels**          | Email (SMTP), SMS (future), WhatsApp (prioritized) |
| **Configuration**     | Tenant-specific credentials with platform fallback |
| **API Endpoints**     | Notification config CRUD operations                |
| **Kafka Integration** | Event-driven notification triggers                 |
| **Fallback Pattern**  | WhatsApp → Email → SMS                             |
| **Security**          | Encrypted credentials, tenant isolation            |

---

## 🎯 Business Purpose

Multi-tenant notification delivery system allowing each tenant to:

- Use their own SMTP/SMS/WhatsApp credentials
- Fallback to platform credentials if not configured
- Send verification codes, welcome emails, alerts
- Track delivery status and analytics

---

## 🏗️ Architecture

### Notification Flow

```
User Service → Kafka (UserCreatedEvent)
                 ↓
Notification Service (Listener)
                 ↓
Check Tenant Config (DB)
                 ↓
Use Tenant Config OR Platform Fallback
                 ↓
Try WhatsApp → Fallback to Email → Fallback to SMS
                 ↓
Log Result (notification_logs)
```

### Configuration Priority

1. **Tenant Config (Database):** If exists, use tenant-specific SMTP/WhatsApp
2. **Platform Fallback (Environment):** If NOT exists, use `info@storeandsale.shop`

---

## 📡 Channels

### Email (SMTP)

- **Status:** ✅ Implemented
- **Priority:** 1 (fallback from WhatsApp)
- **Configuration:** SMTP host, port, username, password, from address
- **Platform Fallback:** `info@storeandsale.shop` (Gmail SMTP)

### WhatsApp

- **Status:** ⏳ Placeholder (future implementation)
- **Priority:** 0 (preferred - lowest cost)
- **Configuration:** API key, from number (+447553838399)
- **Platform Fallback:** `+447553838399`

### SMS

- **Status:** ⏳ Placeholder (future implementation)
- **Priority:** 2 (last resort - highest cost)
- **Configuration:** API key, from number (+447553838399)
- **Platform Fallback:** `+447553838399`

---

## 🗄️ Database Schema

### `notification_configs`

```sql
CREATE TABLE notification_configs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,        -- EMAIL, SMS, WHATSAPP
    provider VARCHAR(50) NOT NULL,       -- SMTP, GMAIL, TWILIO
    is_enabled BOOLEAN DEFAULT true,

    -- SMTP fields
    smtp_host VARCHAR(255),
    smtp_port INTEGER,
    smtp_username VARCHAR(255),
    smtp_password VARCHAR(500),         -- Encrypted
    from_email VARCHAR(255),
    from_name VARCHAR(255),

    -- SMS/WhatsApp fields
    api_key VARCHAR(500),               -- Encrypted
    from_number VARCHAR(50),

    priority INTEGER DEFAULT 1,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted BOOLEAN DEFAULT false,

    UNIQUE(tenant_id, channel, deleted)
);
```

### `notification_logs`

```sql
CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,     -- Kafka event ID (idempotency)
    tenant_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    body TEXT,
    status VARCHAR(50) NOT NULL,        -- PENDING, SENT, FAILED, RETRYING
    error_message TEXT,
    attempts INTEGER DEFAULT 0,
    sent_at TIMESTAMP,
    created_at TIMESTAMP
);
```

---

## 🔌 API Endpoints

### Tenant Config Management

```http
POST   /api/v1/notifications/config
GET    /api/v1/notifications/config
GET    /api/v1/notifications/config/{id}
PUT    /api/v1/notifications/config/{id}
DELETE /api/v1/notifications/config/{id}
```

**Security:** `TENANT_ADMIN` or `SUPER_ADMIN` only

**Example: Create Email Config**

```json
POST /api/v1/notifications/config
{
  "channel": "EMAIL",
  "provider": "SMTP",
  "isEnabled": true,
  "smtpHost": "smtp.acmetekstil.com",
  "smtpPort": 587,
  "smtpUsername": "noreply@acmetekstil.com",
  "smtpPassword": "encrypted_password",
  "fromEmail": "noreply@acmetekstil.com",
  "fromName": "Acme Tekstil",
  "priority": 1
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenantId": "7c9e6679-7425-40de-963d-42a6ee08cd6c",
    "channel": "EMAIL",
    "provider": "SMTP",
    "isEnabled": true,
    "smtpHost": "smtp.acmetekstil.com",
    "smtpPort": 587,
    "smtpUsername": "noreply@acmetekstil.com",
    "smtpPassword": "***MASKED***",
    "fromEmail": "noreply@acmetekstil.com",
    "fromName": "Acme Tekstil",
    "priority": 1
  }
}
```

---

## 📨 Kafka Integration

### Consumed Topics

| Topic                  | Event            | Trigger                 |
| ---------------------- | ---------------- | ----------------------- |
| `user.created`         | UserCreatedEvent | Send verification email |
| `user.password.reset`  | (future)         | Send reset code         |
| `contact.verification` | (future)         | Send verification code  |

### Event Schema

```json
{
  "eventId": "uuid",
  "eventType": "USER_CREATED",
  "tenantId": "uuid",
  "userId": "uuid",
  "email": "user@example.com",
  "phone": "+905551234567",
  "firstName": "Ahmet",
  "companyName": "Acme Tekstil",
  "verificationCode": "123456",
  "timestamp": "2025-10-15T10:00:00Z"
}
```

---

## 🔧 Configuration

### Environment Variables

```bash
# Database
NOTIFICATION_DB_URL=jdbc:postgresql://localhost:5432/notification_db
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Platform Email (Fallback)
PLATFORM_SMTP_HOST=smtp.gmail.com
PLATFORM_SMTP_PORT=587
PLATFORM_SMTP_USERNAME=info@storeandsale.shop
PLATFORM_SMTP_PASSWORD=your-app-password
PLATFORM_EMAIL_FROM=info@storeandsale.shop
PLATFORM_EMAIL_FROM_NAME=Store and Sale

# Platform WhatsApp (Fallback)
PLATFORM_WHATSAPP_ENABLED=true
PLATFORM_WHATSAPP_FROM_NUMBER=+447553838399

# Platform SMS (Fallback)
PLATFORM_SMS_ENABLED=false
PLATFORM_SMS_FROM_NUMBER=+447553838399

# Notification Settings
NOTIFICATION_RETRY_MAX_ATTEMPTS=3
NOTIFICATION_RETRY_BACKOFF_MS=1000
VERIFICATION_CODE_LENGTH=6
VERIFICATION_CODE_EXPIRATION_MINUTES=15
```

---

## 🔐 Security

### Credential Encryption

- **Current:** Passwords and API keys stored in plaintext (development)
- **Future:** Jasypt or AWS KMS encryption

### Tenant Isolation

- All queries filtered by `tenant_id`
- `findByIdAndTenant()` pattern enforced

### Access Control

- Config management: `TENANT_ADMIN` or `SUPER_ADMIN` only
- JWT authentication required

---

## 📊 Monitoring

### Health Check

```bash
curl http://localhost:8084/actuator/health
```

### Metrics (Prometheus)

```
notification_sent_total{channel="email", status="success"}
notification_sent_total{channel="whatsapp", status="failed"}
notification_retry_total{channel="email"}
```

---

## 🚀 Deployment

### Local Development

```bash
# Start dependencies
docker compose up postgres redis kafka -d

# Run service
cd services/notification-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Docker Deployment

```bash
# Build and start all services
mvn clean install -DskipTests
docker compose up -d
```

---

## 🧪 Testing

### Send Test Email (via Kafka)

```bash
# Produce UserCreatedEvent
kafka-console-producer --bootstrap-server localhost:9092 --topic user.created

# Paste JSON:
{
  "eventId": "test-123",
  "eventType": "USER_CREATED",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "7c9e6679-7425-40de-963d-42a6ee08cd6c",
  "email": "test@example.com",
  "firstName": "Test",
  "companyName": "Test Company",
  "verificationCode": "123456"
}
```

### Check Logs

```bash
docker logs -f fabric-notification-service
```

---

## 📝 Future Enhancements

1. **WhatsApp Integration:** Implement WhatsApp Business API
2. **SMS Integration:** Implement Twilio/Vonage
3. **Template Engine:** Advanced HTML email templates
4. **Credential Encryption:** Jasypt/KMS
5. **Delivery Webhooks:** Real-time delivery status
6. **Analytics Dashboard:** Delivery rates, costs, performance

---

## 🐛 Common Issues

### Issue: Email not sending

**Cause:** SMTP password not configured  
**Fix:** Set `PLATFORM_SMTP_PASSWORD` in `.env`

### Issue: All notifications failing

**Cause:** No channels configured  
**Fix:** Create tenant config or ensure platform fallback is configured

---

**Maintainer:** Fabric Management Team  
**Documentation Date:** 2025-10-15
