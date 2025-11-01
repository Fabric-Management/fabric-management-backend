# 📧 Email System - SMTP with Template Management

**Version:** 2.0  
**Last Updated:** 2025-01-29  
**Purpose:** Self-hosted email delivery with professional templates

---

## 🎯 Overview

Email sistemi **SMTP tabanlı** (3. taraf servis yok). Template sistemi sayesinde:
- ✅ HTML template'ler kod dışında (kolay düzenleme)
- ✅ `{{variable}}` placeholder sistemi
- ✅ SMTP ile güvenli email gönderimi
- ✅ Sıfır ek maliyet (kendi SMTP sunucunuz)

---

## ✅ Completed: Template System

### Template Service
- `EmailTemplateService`: Template dosyalarından yükleme ve değişken değiştirme
- Template'ler: `src/main/resources/templates/emails/`
- Placeholder format: `{{variableName}}`

### Available Templates
1. **self-service-welcome.html** - Self-service kayıt email'i
2. **sales-led-welcome.html** - Sales-led onboarding email'i

### Usage Example
```java
Map<String, String> variables = Map.of(
    "firstName", "Ahmet",
    "companyName", "ABC Tekstil",
    "setupUrl", "http://localhost:3000/setup?token=xyz",
    "email", "ahmet@example.com"
);

String html = emailTemplateService.render("self-service-welcome.html", variables);
notificationService.sendNotification(email, subject, html);
```

---

## 🔧 SMTP Configuration

### Current Setup (Hostinger SMTP)
```yaml
# application-local.yml
spring:
  mail:
    host: smtp.hostinger.com
    port: 465
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true
          starttls:
            enable: true

application:
  mail:
    from-email: ${MAIL_FROM_EMAIL}
    from-name: ${MAIL_FROM_NAME:FabricOS}
```

### Environment Variables
```bash
# .env.local (development)
MAIL_HOST=smtp.hostinger.com
MAIL_PORT=465
MAIL_USERNAME=info@yourdomain.com
MAIL_PASSWORD=your_password
MAIL_FROM_EMAIL=info@yourdomain.com
MAIL_FROM_NAME=FabricOS

# Production (via environment variables)
MAIL_HOST=${MAIL_HOST}
MAIL_PORT=${MAIL_PORT}
MAIL_USERNAME=${MAIL_USERNAME}
MAIL_PASSWORD=${MAIL_PASSWORD}
MAIL_FROM_EMAIL=${MAIL_FROM_EMAIL}
MAIL_FROM_NAME=${MAIL_FROM_NAME}
```

---

## 📋 Email Sending Flow

```
1. Controller/Service
   └─> EmailTemplateService.render("template.html", variables)
       └─> Loads template from resources/templates/emails/
       └─> Replaces {{variables}} with actual values
       └─> Returns HTML string

2. NotificationService
   └─> Receives HTML content
   └─> Calls EmailStrategy.sendEmail()

3. EmailStrategy (SMTP)
   └─> Uses JavaMailSender
   └─> Sends via configured SMTP server
   └─> Logs success/failure
```

---

## 🎨 Template Best Practices

### 1. Template Location
- Place templates in: `src/main/resources/templates/emails/`
- File naming: `kebab-case.html` (e.g., `self-service-welcome.html`)

### 2. Variable Naming
- Use descriptive names: `{{firstName}}`, `{{companyName}}`, `{{setupUrl}}`
- Keep it consistent across templates

### 3. Email Design Guidelines
- **Max width:** 560px (email client compatibility)
- **Responsive:** Use inline styles (no external CSS)
- **Fallback fonts:** `'Inter', system-ui, -apple-system, sans-serif`
- **Color contrast:** WCAG AA compliant
- **Button styling:** Matches frontend design system

### 4. Placeholder Replacement
```html
<!-- Template -->
<p>Hello {{firstName}}!</p>
<a href="{{setupUrl}}">Click here</a>

<!-- Usage -->
Map.of("firstName", "Ahmet", "setupUrl", "http://...")
```

---

## 📊 Benefits of This Approach

### ✅ Advantages
- **Zero external dependency** - No 3rd party service
- **Full control** - Your SMTP server, your rules
- **No vendor lock-in** - Switch SMTP provider anytime
- **Template management** - Easy to edit without code changes
- **Cost-effective** - Only SMTP hosting cost (usually included in hosting)

### ⚠️ Considerations
- **SMTP server maintenance** - You need a reliable SMTP server
- **Deliverability** - Requires proper SPF/DKIM setup
- **No built-in analytics** - Need to add tracking if needed
- **Rate limiting** - SMTP provider may have limits

---

## 🔐 SMTP Security & Deliverability

### 1. SPF Record
Add to your DNS:
```
v=spf1 include:_spf.hostinger.com ~all
```

### 2. DKIM
Configure in SMTP provider dashboard (Hostinger cPanel/Email settings)

### 3. DMARC
Add to DNS (optional but recommended):
```
_dmarc.yourdomain.com TXT "v=DMARC1; p=quarantine; rua=mailto:dmarc@yourdomain.com"
```

### 4. Email Authentication
- ✅ Use `From` email matching SMTP username domain
- ✅ Use consistent `From Name`
- ✅ Avoid spam trigger words in subject line

---

## 🛠️ Adding New Templates

### Step 1: Create Template File
```bash
# Create new template
src/main/resources/templates/emails/password-reset.html
```

### Step 2: Design Template
```html
<!DOCTYPE html>
<html>
<body>
    <p>Hello {{firstName}},</p>
    <p>Click here to reset password: <a href="{{resetUrl}}">Reset</a></p>
</body>
</html>
```

### Step 3: Use in Code
```java
Map<String, String> vars = Map.of(
    "firstName", user.getFirstName(),
    "resetUrl", generateResetUrl(token)
);
String html = emailTemplateService.render("password-reset.html", vars);
notificationService.sendNotification(email, "Reset Password", html);
```

---

## 📈 Monitoring & Troubleshooting

### Logs
Email gönderimi loglanır:
```
✅ Email sent successfully to: ah***@example.com
❌ Failed to send email to: ah***@example.com
```

### Common Issues

**1. Connection Timeout**
```
Solution: Check MAIL_HOST and MAIL_PORT are correct
```

**2. Authentication Failed**
```
Solution: Verify MAIL_USERNAME and MAIL_PASSWORD
```

**3. Email in Spam**
```
Solution: 
- Set up SPF/DKIM records
- Use professional "From" name/email
- Avoid spam trigger words
- Send from verified domain
```

---

## 🎯 Architecture

```
┌─────────────────┐
│   Controllers   │
│  (PublicSignup) │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Notification    │
│   Service       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      ┌──────────────────┐
│  EmailTemplate  │      │   EmailStrategy  │
│     Service     │─────▶│     (SMTP)       │
└─────────────────┘      └────────┬─────────┘
         │                         │
         │                         ▼
         │                ┌──────────────────┐
         │                │  JavaMailSender  │
         │                │   (Spring Mail)  │
         │                └────────┬─────────┘
         │                         │
         │                         ▼
         │                ┌──────────────────┐
         │                │   SMTP Server     │
         │                │  (Hostinger/etc) │
         └────────────────┘                   │
                                               │
         ┌─────────────────────────────────────┘
         │
         ▼
┌─────────────────┐
│   Email Inbox   │
│   (Recipient)   │
└─────────────────┘
```

---

## ✅ Action Items (Completed)

- [x] Create `EmailTemplateService`
- [x] Move inline HTML to template files
- [x] Remove inline HTML from controllers
- [x] Update `PublicSignupController` to use templates
- [x] Update `TenantOnboardingService` to use templates
- [x] Document SMTP configuration

---

## 📚 Resources

- [Spring Mail Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#mail)
- [Email Design Best Practices](https://www.campaignmonitor.com/dev-resources/guides/coding/)
- [SPF Record Setup](https://www.sparkpost.com/blog/spf-record/)
- [DKIM Configuration](https://www.dkim.org/)

---

## 🔄 Migration from Inline HTML

### Before
```java
String message = String.format("""
    <p>Hello %s!</p>
    <a href="%s">Click here</a>
    """, firstName, url);
```

### After
```java
Map<String, String> vars = Map.of(
    "firstName", firstName,
    "url", url
);
String message = emailTemplateService.render("template.html", vars);
```

**Benefits:**
- ✅ Template'ler kod dışında (kolay düzenleme)
- ✅ Frontend designer template'leri düzenleyebilir
- ✅ Version control'de daha temiz diff'ler
- ✅ Reusable template'ler

---

**Status:** ✅ **Production Ready** - Template system implemented, SMTP configured, all inline HTML removed.

**No 3rd party services needed** - Fully self-hosted email solution! 🎉
