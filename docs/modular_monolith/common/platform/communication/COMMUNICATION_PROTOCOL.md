# 💬 COMMUNICATION MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/platform/communication`  
**Dependencies:** `common/infrastructure/persistence`, `common/infrastructure/events`

---

## 🎯 MODULE PURPOSE

Communication module, **Multi-Channel Communication** sağlar.

### **Core Responsibilities**

- ✅ **Email Notifications** - SMTP-based email
- ✅ **SMS Notifications** - AWS SNS integration
- ✅ **WhatsApp Notifications** - WhatsApp Business API
- ✅ **Verification Strategies** - Multi-channel verification
- ✅ **Notification Queue** - Async notification delivery
- ✅ **Template Management** - Notification templates

---

## 🧱 MODULE STRUCTURE

```
communication/
├─ api/
│  ├─ controller/
│  │  └─ NotificationController.java
│  └─ facade/
│     └─ NotificationFacade.java
├─ app/
│  ├─ NotificationService.java
│  └─ VerificationService.java
├─ domain/
│  ├─ Notification.java
│  ├─ NotificationTemplate.java
│  └─ event/
│     ├─ NotificationSentEvent.java
│     └─ VerificationSentEvent.java
├─ infra/
│  ├─ repository/
│  │  ├─ NotificationRepository.java
│  │  └─ NotificationTemplateRepository.java
│  ├─ strategy/
│  │  ├─ VerificationStrategy.java      # Interface
│  │  ├─ WhatsAppStrategy.java          # Priority 1
│  │  ├─ EmailStrategy.java             # Priority 2
│  │  └─ SmsStrategy.java               # Priority 3
│  └─ client/
│     ├─ WhatsAppClient.java
│     ├─ EmailClient.java
│     └─ SmsClient.java
└─ dto/
   ├─ SendNotificationRequest.java
   └─ VerificationRequest.java
```

---

## 📋 VERIFICATION STRATEGIES

### **Strategy Priority**

```
Priority 1: WhatsApp (Fastest, Highest open rate)
  ├─ Check if WhatsApp configured
  ├─ Check if recipient has WhatsApp
  └─ If available → Send via WhatsApp

Priority 2: Email (Universal, Professional)
  ├─ Check if Email configured
  └─ Send via Email

Priority 3: SMS/AWS SNS (Fallback, Reliable)
  ├─ Check if AWS SNS configured
  └─ Send via SMS
```

### **Verification Strategy Interface**

```java
public interface VerificationStrategy {

    /**
     * Check if strategy is available
     *
     * @return true if configured and operational
     */
    boolean isAvailable();

    /**
     * Send verification code
     *
     * @param recipient Email or phone number
     * @param code Verification code
     */
    void sendVerificationCode(String recipient, String code);

    /**
     * Strategy priority (1 = highest)
     *
     * @return Priority number
     */
    int priority();

    /**
     * Strategy name
     *
     * @return Strategy name
     */
    String name();
}
```

---

## 📋 DOMAIN MODELS

### **Notification Entity**

```java
@Entity
@Table(name = "common_notification", schema = "common_communication")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private String recipient; // Email or phone

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel; // EMAIL, SMS, WHATSAPP

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type; // VERIFICATION, PASSWORD_RESET, GENERAL

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column
    private Instant sentAt;

    @Column
    private Integer retryCount;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
```

---

## 🔗 ENDPOINTS

| Endpoint                                | Method | Purpose                | Auth Required  |
| --------------------------------------- | ------ | ---------------------- | -------------- |
| `/api/common/notifications`             | GET    | List notifications     | ✅ Yes (ADMIN) |
| `/api/common/notifications/{id}`        | GET    | Get notification by ID | ✅ Yes (ADMIN) |
| `/api/common/notifications/send`        | POST   | Send notification      | ✅ Yes (ADMIN) |
| `/api/common/notifications/verify/send` | POST   | Send verification code | ❌ No          |

---

## 🔄 EVENTS

| Event                   | Trigger                | Listeners        |
| ----------------------- | ---------------------- | ---------------- |
| `NotificationSentEvent` | Notification sent      | Audit, Analytics |
| `VerificationSentEvent` | Verification code sent | Audit            |

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team
