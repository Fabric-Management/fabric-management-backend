# 📝 LOGGING PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-10-25  
**Module:** `common/util`  
**Purpose:** Logging standards, PII masking, GDPR/KVKK compliance

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Logging Levels](#logging-levels)
3. [PII Masking](#pii-masking)
4. [Best Practices](#best-practices)
5. [Production Configuration](#production-configuration)

---

## 🎯 OVERVIEW

Fabric Management platformu, **GDPR/KVKK compliant** logging stratejisi kullanır. Tüm PII (Personally Identifiable Information) production ortamında otomatik maskelenir.

### **Core Principles**

| Prensip                | Açıklama                                 |
| ---------------------- | ---------------------------------------- |
| **Profile-Aware**      | Local'de full data, production'da masked |
| **Zero Configuration** | Otomatik profile detection               |
| **Type-Specific**      | Email, phone, card için özel masking     |
| **Performance**        | Zero overhead (compile-time decision)    |
| **Compliance**         | GDPR/KVKK/ISO 27001/PCI DSS ready        |

---

## 📊 LOGGING LEVELS

### **Development (local profile)**

```yaml
# application-local.yml
logging:
  level:
    root: INFO
    com.fabricmanagement: DEBUG
    org.springframework.web: INFO
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG # SQL queries visible
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE # Query params visible
```

**Purpose:** Full visibility for debugging

### **Production (prod profile)**

```yaml
# application-prod.yml
logging:
  level:
    root: WARN
    com.fabricmanagement: INFO
    org.springframework.web: WARN
    org.springframework.security: WARN
    org.hibernate: WARN
    org.hibernate.SQL: WARN # ⭐ SQL queries hidden
    org.hibernate.type.descriptor.sql.BasicBinder: WARN # ⭐ Query params hidden
```

**Purpose:** Performance + Security (no PII in SQL logs)

---

## 🔐 PII MASKING

### **PiiMaskingUtil Location**

```
common/util/PiiMaskingUtil.java
```

### **Masking Methods**

#### 1. **Email Masking**

```java
import com.fabricmanagement.common.util.PiiMaskingUtil;

String email = "john.doe@example.com";
String masked = PiiMaskingUtil.maskEmail(email);

// Local profile:  "john.doe@example.com"
// Prod profile:   "jo***@example.com"
```

**Algorithm:**

- Show first 2 characters of local part
- Mask rest with `***`
- Keep full domain for debugging

**Examples:**

| Original                        | Local                           | Production                      |
| ------------------------------- | ------------------------------- | ------------------------------- |
| `fatih@akkayalartekstil.com.tr` | `fatih@akkayalartekstil.com.tr` | `fa***@akkayalartekstil.com.tr` |
| `a@test.com`                    | `a@test.com`                    | `a***@test.com`                 |
| `john.doe@example.com`          | `john.doe@example.com`          | `jo***@example.com`             |

#### 2. **Phone Masking**

```java
String phone = "+905551234567";
String masked = PiiMaskingUtil.maskPhone(phone);

// Local profile:  "+905551234567"
// Prod profile:   "+905***4567"
```

**Algorithm:**

- Show first 3 characters (country code + carrier)
- Mask middle with `***`
- Show last 4 digits for verification

**Examples:**

| Original        | Local           | Production    |
| --------------- | --------------- | ------------- |
| `+905551234567` | `+905551234567` | `+905***4567` |
| `05551234567`   | `05551234567`   | `055***4567`  |

#### 3. **Card Number Masking**

```java
String card = "1234567890123456";
String masked = PiiMaskingUtil.maskCardNumber(card);

// Local profile:  "1234567890123456"
// Prod profile:   "1234***3456"
```

**Algorithm:**

- Show first 4 digits (BIN)
- Mask middle with `***`
- Show last 4 digits
- PCI DSS Level 1 compliant

#### 4. **Generic Masking**

```java
String sensitive = "SecretValue123";
String masked = PiiMaskingUtil.mask(sensitive);

// Local profile:  "SecretValue123"
// Prod profile:   "S***3"
```

**Algorithm:**

- Show first and last character only
- Mask middle with `***`

---

## 🔧 IMPLEMENTATION

### **Usage in Services**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    public String checkEligibility(RegisterCheckRequest request) {
        // ✅ Good: PII masked
        log.info("Registration check: contactValue={}",
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        // Business logic...
    }
}
```

### **Usage in Controllers**

```java
@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    @PostMapping("/register/check")
    public ResponseEntity<?> checkEligibility(@RequestBody RegisterCheckRequest request) {
        // ✅ Good: PII masked
        log.info("Registration endpoint: contactValue={}",
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        // Delegate to service...
    }
}
```

### **Current Integration**

| Class                 | Methods     | Status        |
| --------------------- | ----------- | ------------- |
| `AuthController`      | 3 endpoints | ✅ Integrated |
| `RegistrationService` | 3 methods   | ✅ Integrated |
| `LoginService`        | 5 methods   | ✅ Integrated |
| `UserService`         | 2 methods   | ✅ Integrated |
| `UserController`      | 2 endpoints | ✅ Integrated |

**Total:** 15 log statements with PII masking

---

## ✅ BEST PRACTICES

### **1. Always Mask PII**

```java
// ✅ Good: Masked
log.info("User login: {}", PiiMaskingUtil.maskEmail(email));

// ❌ Bad: Raw PII (GDPR violation in production)
log.info("User login: {}", email);
```

### **2. Choose Appropriate Masking Method**

```java
// Email
log.info("Email sent: {}", PiiMaskingUtil.maskEmail(email));

// Phone
log.info("SMS sent: {}", PiiMaskingUtil.maskPhone(phone));

// Credit Card
log.info("Payment: {}", PiiMaskingUtil.maskCardNumber(card));

// Generic sensitive data
log.info("API key: {}", PiiMaskingUtil.mask(apiKey));
```

### **3. UUID/IDs Don't Need Masking**

```java
// ✅ Good: UUIDs are not PII
log.info("User ID: {}", userId);
log.info("Tenant ID: {}", tenantId);

// ❌ Not needed: UUIDs are safe
log.info("User ID: {}", PiiMaskingUtil.mask(userId.toString())); // Unnecessary
```

### **4. Non-Sensitive Data**

```java
// ✅ No masking needed for non-PII
log.info("Material created: name={}", materialName);
log.info("Order status: {}", orderStatus);
log.info("Job assigned: jobId={}", jobId);
```

### **5. Structured Logging**

```java
// ✅ Good: Structured, masked PII
log.info("User action - userId: {}, email: {}, action: {}",
    userId,
    PiiMaskingUtil.maskEmail(email),
    action);

// ❌ Bad: Unstructured
log.info("User " + email + " performed " + action); // No masking, hard to parse
```

---

## 🚀 PRODUCTION CONFIGURATION

### **SQL Logging Suppression**

Production'da SQL query'leri loglara yazılmaz (performance + security):

```yaml
# application-prod.yml
logging:
  level:
    org.hibernate.SQL: WARN # No SQL queries
    org.hibernate.type.descriptor.sql.BasicBinder: WARN # No query parameters
```

**Development vs Production:**

```sql
-- ❌ DEVELOPMENT (local profile) - Full visibility
2025-10-25 13:13:09 -
    select u1_0.* from common_user.common_user u1_0 where u1_0.contact_value=?
2025-10-25 13:13:09 - binding parameter [1] as [VARCHAR] - [fatih@example.com]

-- ✅ PRODUCTION (prod profile) - No SQL logs
[No SQL output - WARN level suppresses all queries]
```

### **Log File Configuration**

```yaml
# application-prod.yml
logging:
  file:
    name: ${LOG_FILE_PATH:/var/log/fabric-management}/application.log
    max-size: ${LOG_FILE_MAX_SIZE:100MB}
    max-history: ${LOG_FILE_MAX_HISTORY:30}
```

---

## 🧪 TESTING

### **Unit Tests**

```java
@Test
void maskEmail_shouldMaskInProduction() {
    // Given
    String email = "john.doe@example.com";

    // When (production profile)
    System.setProperty("spring.profiles.active", "prod");
    String masked = PiiMaskingUtil.maskEmail(email);

    // Then
    assertThat(masked).isEqualTo("jo***@example.com");
}

@Test
void maskEmail_shouldNotMaskInLocal() {
    // Given
    String email = "john.doe@example.com";

    // When (local profile)
    System.setProperty("spring.profiles.active", "local");
    String masked = PiiMaskingUtil.maskEmail(email);

    // Then
    assertThat(masked).isEqualTo("john.doe@example.com");
}
```

---

## 📋 COMPLIANCE CHECKLIST

| Standard      | Requirement                         | Implementation              | Status |
| ------------- | ----------------------------------- | --------------------------- | ------ |
| **GDPR**      | Article 32 - Security of processing | PII masking in logs         | ✅     |
| **KVKK**      | Article 12 - Data security          | Profile-aware masking       | ✅     |
| **ISO 27001** | A.12.4.1 - Event logging            | Structured, masked logs     | ✅     |
| **PCI DSS**   | Requirement 3.4 - Card masking      | Card number masking         | ✅     |
| **SOC 2**     | CC6.1 - Logical access              | Access logs with masked PII | ✅     |

---

## 🎯 SUMMARY

### **Key Features**

✅ **Profile-Aware:** Automatic masking in production  
✅ **Type-Specific:** Email, phone, card, generic masking  
✅ **Zero Config:** Works out of the box  
✅ **Performance:** No runtime overhead  
✅ **Compliance:** GDPR, KVKK, ISO 27001, PCI DSS ready

### **Production Benefits**

- ✅ No PII in log files (GDPR Article 17 - Right to erasure)
- ✅ No SQL queries in logs (performance + security)
- ✅ Audit trail compliant (masked but traceable)
- ✅ Support team can debug without seeing PII
- ✅ Log aggregation services (ELK, Splunk) safe

### **Development Benefits**

- ✅ Full data visibility for debugging
- ✅ No masking overhead
- ✅ Easy troubleshooting
- ✅ SQL query optimization

---

## 📚 RELATED DOCUMENTATION

- [SECURITY_POLICIES.md](../../SECURITY_POLICIES.md#6-pii-masking-in-logs-gdprkvkk-compliance) - Security policies
- [FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md](../../../FABRIC_MANAGEMENT_DEVELOPMENT_PROTOCOL.md#-pii-masking-gdprkvkk-compliance) - Development protocol
- [MANIFESTO_COMPLIANCE_REPORT.md](../../../../MANIFESTO_COMPLIANCE_REPORT.md#-gdprkvkk-compliance-new) - Compliance report

---

**Last Updated:** 2025-10-25  
**Maintained By:** Fabric Management Team  
**Status:** ✅ Production Ready
