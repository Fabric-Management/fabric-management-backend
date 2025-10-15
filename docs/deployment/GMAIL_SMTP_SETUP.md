# 📧 Gmail SMTP Setup - Platform Email Configuration

**Email:** info@storeandsale.shop  
**Purpose:** Platform default SMTP for notification-service  
**Last Updated:** 2025-10-15

---

## 🎯 Overview

Notification Service uses Gmail SMTP to send verification emails when tenant has no custom SMTP config.

---

## 📋 Step-by-Step Setup

### Step 1: Gmail Account
- Email: `info@storeandsale.shop`
- Already exists? Login at https://mail.google.com

### Step 2: Enable 2-Factor Authentication
1. Go to: https://myaccount.google.com/security
2. Find "2-Step Verification"
3. Click "Get Started"
4. Follow on-screen instructions (phone verification)

### Step 3: Create App Password
1. Go to: https://myaccount.google.com/apppasswords
2. Select app: "Mail"
3. Select device: "Other (Custom name)" → Type: "Notification Service"
4. Click "Generate"
5. **COPY THE 16-CHARACTER PASSWORD** (shown only once!)
   - Format: `xxxx xxxx xxxx xxxx`
   - Example: `abcd efgh ijkl mnop`

### Step 4: Add to .env File
```bash
# Open .env file
nano .env

# Add these lines:
PLATFORM_SMTP_HOST=smtp.gmail.com
PLATFORM_SMTP_PORT=587
PLATFORM_SMTP_USERNAME=info@storeandsale.shop
PLATFORM_SMTP_PASSWORD=abcd efgh ijkl mnop  # ⚠️ Replace with actual password!
PLATFORM_EMAIL_FROM=info@storeandsale.shop
PLATFORM_EMAIL_FROM_NAME=Store and Sale

# Save: Ctrl+O, Enter, Ctrl+X
```

### Step 5: Restart Services
```bash
docker compose restart notification-service
```

### Step 6: Test
```bash
# Send test registration
POST http://localhost:8080/api/v1/public/onboarding/register
{
  "email": "your-test-email@gmail.com",
  "preferredChannel": "EMAIL",
  ...
}

# Check logs
docker logs -f fabric-notification-service
```

---

## ✅ Verification

### Success Indicators:
```
✅ Email sent: info@storeandsale.shop → user@example.com (tenant: xxx)
✅ Notification sent via EMAIL: user@example.com
```

### Common Errors:

#### Error 1: "535-5.7.8 Username and Password not accepted"
**Cause:** Wrong password or 2FA not enabled  
**Fix:** Regenerate App Password, ensure 2FA is ON

#### Error 2: "PKIX path building failed"
**Cause:** SSL certificate issue  
**Fix:** Update Java keystore or disable SSL verification (NOT recommended for production)

#### Error 3: "Connection timeout"
**Cause:** Firewall blocking port 587  
**Fix:** Check firewall/network settings

---

## 🔒 Security Best Practices

1. **Never commit .env to Git** (already in .gitignore)
2. **Use environment variables in production** (Kubernetes secrets, AWS Secrets Manager)
3. **Rotate App Passwords every 90 days**
4. **Monitor Gmail security dashboard:** https://myaccount.google.com/security

---

## 🌐 Alternative SMTP Providers

If Gmail doesn't work, you can use:

### Option 1: SendGrid (Free tier: 100 emails/day)
```bash
PLATFORM_SMTP_HOST=smtp.sendgrid.net
PLATFORM_SMTP_PORT=587
PLATFORM_SMTP_USERNAME=apikey
PLATFORM_SMTP_PASSWORD=your-sendgrid-api-key
```

### Option 2: Mailgun (Free tier: 5000 emails/month)
```bash
PLATFORM_SMTP_HOST=smtp.mailgun.org
PLATFORM_SMTP_PORT=587
PLATFORM_SMTP_USERNAME=postmaster@your-domain.mailgun.org
PLATFORM_SMTP_PASSWORD=your-mailgun-smtp-password
```

### Option 3: AWS SES (0.10$ per 1000 emails)
```bash
PLATFORM_SMTP_HOST=email-smtp.us-east-1.amazonaws.com
PLATFORM_SMTP_PORT=587
PLATFORM_SMTP_USERNAME=your-ses-smtp-username
PLATFORM_SMTP_PASSWORD=your-ses-smtp-password
```

---

## 📊 Monitoring

### Gmail Quota
- **Free:** 500 emails/day
- **Workspace:** 2000 emails/day per user

### Check Sent Emails
https://mail.google.com/mail/u/0/#sent

### Check Blocked/Bounced
https://mail.google.com/mail/u/0/#spam

---

**Maintainer:** Fabric Management Team  
**Documentation Date:** 2025-10-15

