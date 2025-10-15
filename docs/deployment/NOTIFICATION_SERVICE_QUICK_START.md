# 🚀 Notification Service - Quick Start Guide

**Goal:** Get email notifications working in 15 minutes  
**Last Updated:** 2025-10-15

---

## ✅ Minimal Setup (Email Only)

### Step 1: Gmail App Password (5 min)

1. Go to: https://myaccount.google.com/apppasswords
2. Login as: **info@storeandsale.shop**
3. Create app password → Copy 16-character code
4. **IMPORTANT:** Save it somewhere safe!

---

### Step 2: Update .env File (2 min)

```bash
# Open .env
nano .env

# Add/Update these lines:
PLATFORM_SMTP_PASSWORD=xxxx xxxx xxxx xxxx  # ⚠️ Replace with actual!
PLATFORM_EMAIL_FROM=info@storeandsale.shop
PLATFORM_EMAIL_FROM_NAME=Store and Sale

# Disable WhatsApp/SMS (not implemented yet)
PLATFORM_WHATSAPP_ENABLED=false
PLATFORM_SMS_ENABLED=false

# Save: Ctrl+O, Enter, Ctrl+X
```

---

### Step 3: Build & Deploy (5 min)

```bash
# Build
mvn clean install -DskipTests

# Deploy
docker compose up -d

# Wait for services to start (30 sec)
docker compose ps

# Check notification-service logs
docker logs -f fabric-notification-service
```

**Expected Log:**

```
✅ Platform Fallback Configuration loaded:
   Email: Store and Sale <info@storeandsale.shop>
   SMTP: smtp.gmail.com:587 (username: info@storeandsale.shop)
   WhatsApp: +447553838399 (enabled: false)
```

---

### Step 4: Test Registration (3 min)

**Postman Request:**

```http
POST http://localhost:8080/api/v1/public/onboarding/register
Content-Type: application/json

{
  "companyName": "Test Firma A.Ş.",
  "legalName": "Test Firma Anonim Şirketi",
  "taxId": "1234567890",
  "registrationNumber": "REG-2025-TEST",
  "companyType": "CORPORATION",
  "industry": "MANUFACTURING",
  "addressLine1": "Test Cadde No:1",
  "city": "İstanbul",
  "country": "Turkey",
  "firstName": "Ahmet",
  "lastName": "Test",
  "email": "YOUR-REAL-EMAIL@gmail.com",  // ⚠️ Change this!
  "phone": "+905551234567",
  "preferredChannel": "EMAIL"  // ✅ Email ile gönder
}
```

**Expected Response:**

```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "companyId": "...",
    "userId": "...",
    "email": "YOUR-REAL-EMAIL@gmail.com",
    "nextStep": "Please check your email to verify your account"
  }
}
```

---

### Step 5: Check Email (1 min)

**Check your inbox for:**

```
From: Store and Sale <info@storeandsale.shop>
Subject: Verify your account - Test Firma A.Ş.
Body:
  Welcome to Test Firma A.Ş.!

  Please verify your email address using the code below:
  123456

  This code will expire in 15 minutes.
```

---

## 🐛 Troubleshooting

### Issue 1: No email received

**Check 1: SMTP Password**

```bash
# Verify password is set
docker exec fabric-notification-service env | grep PLATFORM_SMTP_PASSWORD
```

**Check 2: Service Logs**

```bash
docker logs fabric-notification-service | grep "Email"

# Expected:
✅ Email sent: info@storeandsale.shop → user@example.com
```

**Check 3: Kafka Event**

```bash
docker logs fabric-notification-service | grep "UserCreatedEvent"

# Expected:
📨 Received UserCreatedEvent: user-id (tenant: tenant-id, preferredChannel: EMAIL)
```

---

### Issue 2: "535-5.7.8 Username and Password not accepted"

**Cause:** Wrong SMTP password or 2FA not enabled

**Fix:**

1. Enable 2FA: https://myaccount.google.com/security
2. Create new App Password: https://myaccount.google.com/apppasswords
3. Update .env with new password
4. Restart: `docker compose restart notification-service`

---

### Issue 3: Email in spam folder

**Cause:** Gmail marks unfamiliar senders as spam

**Fix:**

1. Check spam folder
2. Mark as "Not Spam"
3. Add `info@storeandsale.shop` to contacts

---

## 📊 Verify Everything Works

### Database Check

```sql
-- Connect to notification_db
docker exec -it fabric-postgres psql -U postgres -d notification_db

-- Check notification logs
SELECT id, channel, recipient, status, sent_at
FROM notification_logs
ORDER BY created_at DESC
LIMIT 10;

-- Expected: 1 row with status='SENT', channel='EMAIL'
```

### Kafka Check

```bash
# Check if topic exists
docker exec -it fabric-kafka kafka-topics --bootstrap-server localhost:9092 --list | grep user.created

# Check messages (if any)
docker exec -it fabric-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic user.created \
  --from-beginning \
  --max-messages 1
```

---

## 🎯 Success Criteria

✅ Registration endpoint returns success  
✅ Email received in inbox (or spam)  
✅ Verification code visible (6 digits)  
✅ notification_logs table has 1 SENT record  
✅ No errors in logs

---

## 🚀 Next Steps

1. **Test mobile flow:** Set `preferredChannel: "WHATSAPP"` (will fallback to Email for now)
2. **Test SMS flow:** Set `preferredChannel: "SMS"` (will fallback to Email for now)
3. **Implement WhatsApp:** See `docs/deployment/WHATSAPP_SMS_SETUP.md`
4. **Implement SMS:** See `docs/deployment/WHATSAPP_SMS_SETUP.md`

---

## 📝 Summary

**What Works Today:**

- ✅ Email notifications via Gmail SMTP
- ✅ Verification code delivery
- ✅ Multi-channel fallback (WhatsApp/SMS → Email)
- ✅ Delivery tracking

**What's Coming:**

- ⏳ WhatsApp notifications (placeholder ready)
- ⏳ SMS notifications (placeholder ready)
- ⏳ HTML email templates
- ⏳ Tenant-specific SMTP configs

---

**Maintainer:** Fabric Management Team  
**Documentation Date:** 2025-10-15
