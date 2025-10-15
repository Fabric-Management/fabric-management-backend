# 📱 WhatsApp & SMS Setup - Platform Configuration

**Phone Number:** +447553838399  
**Purpose:** Platform default WhatsApp/SMS for notification-service  
**Last Updated:** 2025-10-15  
**Status:** 🚧 Future Implementation (Placeholder Ready)

---

## 🎯 Current Status

| Channel      | Implementation | Production Ready               |
| ------------ | -------------- | ------------------------------ |
| **Email**    | ✅ Complete    | ✅ Yes                         |
| **WhatsApp** | ⏳ Placeholder | ❌ No (API integration needed) |
| **SMS**      | ⏳ Placeholder | ❌ No (API integration needed) |

---

## 📋 WhatsApp Business API Setup (Future)

### Option 1: WhatsApp Business API (Official)

#### Requirements:

1. **WhatsApp Business Account**

   - Apply: https://business.whatsapp.com
   - Approval time: 1-2 weeks
   - Requires: Business verification, phone number ownership

2. **Meta Developer Account**

   - Register: https://developers.facebook.com
   - Create app → WhatsApp Business API

3. **Phone Number: +447553838399**
   - Must be registered with WhatsApp Business
   - Cannot be used on personal WhatsApp simultaneously

#### Configuration:

```bash
# .env file
PLATFORM_WHATSAPP_ENABLED=true
PLATFORM_WHATSAPP_PROVIDER=WHATSAPP_BUSINESS
PLATFORM_WHATSAPP_API_KEY=your-meta-api-key
PLATFORM_WHATSAPP_FROM_NUMBER=+447553838399
PLATFORM_WHATSAPP_PHONE_ID=your-phone-number-id
PLATFORM_WHATSAPP_BUSINESS_ACCOUNT_ID=your-business-account-id
```

#### Code Changes Required:

```java
// services/notification-service/.../WhatsAppNotificationSender.java

@Override
public void send(NotificationSendRequestEvent event, NotificationConfig config) {
    // Get API credentials
    String apiKey = config != null ? config.getApiKey() : platformConfig.getPlatformWhatsappApiKey();
    String fromNumber = config != null ? config.getFromNumber() : platformConfig.getPlatformWhatsappFromNumber();

    // Build WhatsApp API request
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + apiKey);
    headers.setContentType(MediaType.APPLICATION_JSON);

    Map<String, Object> request = Map.of(
        "messaging_product", "whatsapp",
        "to", event.getRecipient(), // Must be in E.164 format
        "type", "template", // or "text" for non-template messages
        "template", Map.of(
            "name", "verification_code",
            "language", Map.of("code", "en"),
            "components", List.of(
                Map.of("type", "body", "parameters", List.of(
                    Map.of("type", "text", "text", event.getVariables().get("code"))
                ))
            )
        )
    );

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

    String url = "https://graph.facebook.com/v18.0/" + phoneNumberId + "/messages";
    ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

    if (!response.getStatusCode().is2xxSuccessful()) {
        throw new NotificationException("WhatsApp delivery failed", "WHATSAPP", event.getRecipient(), true);
    }

    log.info("✅ WhatsApp sent: {} → {}", fromNumber, event.getRecipient());
}
```

#### Cost:

- **Free tier:** First 1000 conversations/month
- **After:** $0.0042 - $0.07 per conversation (varies by country)

---

### Option 2: Twilio WhatsApp API (Easier, Faster)

#### Requirements:

1. **Twilio Account**

   - Sign up: https://www.twilio.com
   - Verify phone: +447553838399

2. **WhatsApp Sender (Twilio Sandbox or Approved Number)**
   - Sandbox: Free, instant, limited (testing only)
   - Approved: Requires Meta approval, production-ready

#### Configuration:

```bash
# .env file
PLATFORM_WHATSAPP_ENABLED=true
PLATFORM_WHATSAPP_PROVIDER=TWILIO
PLATFORM_WHATSAPP_API_KEY=your-twilio-auth-token
PLATFORM_WHATSAPP_ACCOUNT_SID=your-twilio-account-sid
PLATFORM_WHATSAPP_FROM_NUMBER=whatsapp:+447553838399
```

#### Code Changes:

```java
// Use Twilio Java SDK
Twilio.init(accountSid, authToken);

Message message = Message.creator(
    new PhoneNumber("whatsapp:" + event.getRecipient()),
    new PhoneNumber("whatsapp:" + fromNumber),
    event.getBody()
).create();

log.info("✅ WhatsApp sent via Twilio: {}", message.getSid());
```

#### Cost:

- **Sandbox:** Free (testing only)
- **Production:** $0.005 per message (very cheap!)

---

## 📱 SMS Setup (Future)

### Option 1: Twilio SMS (Recommended)

#### Requirements:

1. **Twilio Account** (same as WhatsApp)
2. **Phone Number: +447553838399**
   - Buy from Twilio: £1-10/month
   - Or verify existing number

#### Configuration:

```bash
# .env file
PLATFORM_SMS_ENABLED=true
PLATFORM_SMS_PROVIDER=TWILIO
PLATFORM_SMS_API_KEY=your-twilio-auth-token
PLATFORM_SMS_ACCOUNT_SID=your-twilio-account-sid
PLATFORM_SMS_FROM_NUMBER=+447553838399
```

#### Code Changes:

```java
// services/notification-service/.../SmsNotificationSender.java

@Override
public void send(NotificationSendRequestEvent event, NotificationConfig config) {
    String accountSid = config != null ? config.getAccountSid() : platformConfig.getPlatformSmsAccountSid();
    String authToken = config != null ? config.getApiKey() : platformConfig.getPlatformSmsApiKey();
    String fromNumber = config != null ? config.getFromNumber() : platformConfig.getPlatformSmsFromNumber();

    Twilio.init(accountSid, authToken);

    Message message = Message.creator(
        new PhoneNumber(event.getRecipient()),
        new PhoneNumber(fromNumber),
        event.getBody()
    ).create();

    if (message.getStatus() == Message.Status.FAILED) {
        throw new NotificationException("SMS delivery failed", "SMS", event.getRecipient(), true);
    }

    log.info("✅ SMS sent: {} → {} (SID: {})", fromNumber, event.getRecipient(), message.getSid());
}
```

#### Cost:

- **UK:** £0.04 per SMS (~$0.05)
- **Turkey:** £0.02 per SMS (~$0.025)
- **Expensive!** Use WhatsApp when possible

---

## 🔄 Priority Order (Cost Optimization)

```
1. WhatsApp (£0.004/msg) → Cheapest
2. Email (Free with Gmail) → Free
3. SMS (£0.04/msg) → Most Expensive
```

**Strategy:** Always try WhatsApp first, fallback to Email, SMS as last resort.

---

## 📊 Minimal Working Setup (Today)

### Without WhatsApp/SMS:

```bash
# .env - ONLY EMAIL WORKS
PLATFORM_SMTP_PASSWORD=your-gmail-app-password
PLATFORM_EMAIL_FROM=info@storeandsale.shop

# Disable WhatsApp/SMS
PLATFORM_WHATSAPP_ENABLED=false
PLATFORM_SMS_ENABLED=false
```

**Result:**

- Email ✅ Works
- WhatsApp ❌ Logs warning, falls back to Email
- SMS ❌ Logs warning, falls back to Email

---

## 🚀 Production-Ready Checklist

### Phase 1: Email Only (Current)

- [x] Gmail App Password configured
- [x] Email sender implemented
- [x] Fallback to email works
- [ ] Test with real registration
- [ ] Monitor Gmail quota (500/day)

### Phase 2: WhatsApp Integration (Future)

- [ ] Choose provider (Meta or Twilio)
- [ ] Register WhatsApp Business (+447553838399)
- [ ] Implement WhatsAppNotificationSender
- [ ] Test sandbox
- [ ] Get production approval
- [ ] Deploy

### Phase 3: SMS Integration (Future)

- [ ] Buy Twilio number (+447553838399)
- [ ] Implement SmsNotificationSender
- [ ] Test with real phone
- [ ] Monitor costs
- [ ] Deploy

---

## 💰 Cost Estimation (1000 users/month)

| Scenario                    | Cost               |
| --------------------------- | ------------------ |
| **100% Email**              | £0 (Free Gmail)    |
| **50% WhatsApp, 50% Email** | £2 (500 x £0.004)  |
| **100% WhatsApp**           | £4 (1000 x £0.004) |
| **100% SMS**                | £40 (1000 x £0.04) |

**Recommendation:** Prioritize WhatsApp, use Email as fallback, avoid SMS unless necessary.

---

## 🔐 Security Notes

1. **API Keys:** Never commit to Git, use environment variables
2. **Phone Number Verification:** Ensure +447553838399 is owned by your business
3. **Rate Limiting:** WhatsApp has rate limits (1000 msg/sec), SMS too
4. **Compliance:** GDPR, TCPA (USA), DPA (UK) - get user consent

---

## 📝 Next Steps (After Email Works)

1. **Test Email thoroughly** (this week)
2. **Create Twilio account** (1 hour)
3. **Setup WhatsApp sandbox** (1 hour)
4. **Implement WhatsApp sender** (4 hours)
5. **Test WhatsApp** (1 hour)
6. **Production approval** (1-2 weeks)
7. **Deploy** (30 min)

---

**Maintainer:** Fabric Management Team  
**Documentation Date:** 2025-10-15
