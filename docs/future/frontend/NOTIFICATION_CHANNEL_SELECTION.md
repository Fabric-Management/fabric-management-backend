# 📱 Notification Channel Selection - Frontend Integration

**Version:** 1.0.0  
**Last Updated:** 2025-10-15  
**Status:** ✅ Production Ready

---

## 🎯 Overview

Kullanıcılar kayıt sırasında doğrulama kodunu **WhatsApp** (default, mobil için) veya **SMS** ile almayı seçebilirler.

---

## 📱 Mobile Flow (Default: WhatsApp)

### Registration Screen UI

```jsx
// ✅ Default: WhatsApp aktif
const [notificationChannel, setNotificationChannel] = useState('WHATSAPP');

<Switch
  label="SMS ile doğrulama kodu al"
  value={notificationChannel === 'SMS'}
  onValueChange={(enabled) => 
    setNotificationChannel(enabled ? 'SMS' : 'WHATSAPP')
  }
/>

// Registration request
POST /api/v1/public/onboarding/register
{
  "companyName": "Acme Tekstil A.Ş.",
  "firstName": "Ahmet",
  "lastName": "Yılmaz",
  "email": "admin@acmetekstil.com",
  "phone": "+905551234567",
  "preferredChannel": "WHATSAPP"  // ✅ or "SMS"
  // ... other fields
}
```

---

## 💻 Web Flow (Default: Email)

### Registration Screen UI

```jsx
// ✅ Default: Email aktif (web)
const [notificationChannel, setNotificationChannel] = useState('EMAIL');

<RadioGroup value={notificationChannel} onChange={setNotificationChannel}>
  <Radio value="EMAIL">Email ile doğrulama kodu al</Radio>
  <Radio value="WHATSAPP">WhatsApp ile doğrulama kodu al</Radio>
  <Radio value="SMS">SMS ile doğrulama kodu al</Radio>
</RadioGroup>

// Registration request
POST /api/v1/public/onboarding/register
{
  "companyName": "Acme Tekstil A.Ş.",
  "firstName": "Ahmet",
  "lastName": "Yılmaz",
  "email": "admin@acmetekstil.com",
  "phone": "+905551234567",
  "preferredChannel": "EMAIL"  // ✅ or "WHATSAPP" or "SMS"
  // ... other fields
}
```

---

## 🔄 Backend Flow

### 1. User Service (Onboarding)
```java
// ✅ Request DTO'da preferredChannel var
public class TenantRegistrationRequest {
    private String email;
    private String phone;
    private String preferredChannel; // "WHATSAPP", "EMAIL", "SMS"
}

// Kafka event'e ekle
UserCreatedEvent event = UserCreatedEvent.builder()
    .email(request.getEmail())
    .phone(request.getPhone())
    .preferredChannel(request.getPreferredChannel()) // ✅ Frontend'den geliyor
    .verificationCode(generatedCode)
    .build();

kafkaTemplate.send("user.created", event);
```

### 2. Notification Service (Listener)
```java
@KafkaListener(topics = "user.created")
public void onUserCreated(UserCreatedEvent event) {
    NotificationChannel channel = determineChannel(event);
    
    // WhatsApp/SMS → phone, Email → email
    String recipient = (channel == EMAIL) ? event.getEmail() : event.getPhone();
    
    // Notification gönder
    dispatchService.dispatch(notificationEvent);
}
```

### 3. Fallback Pattern
```
1. Preferred channel (WhatsApp/SMS/Email) → Try first
2. If fails → Fallback to Email (always available)
3. If Email fails → Fallback to SMS (last resort)
```

---

## 📊 Channel Comparison

| Channel | Cost | Speed | Reliability | Mobile Default | Web Default |
|---------|------|-------|-------------|----------------|-------------|
| **WhatsApp** | 💰 Lowest | ⚡ Fast | ✅ High | ✅ Yes | ❌ No |
| **Email** | 💰💰 Low | ⚡⚡ Medium | ✅ High | ❌ No | ✅ Yes |
| **SMS** | 💰💰💰 High | ⚡ Fast | ✅ High | ❌ No | ❌ No |

---

## 🎨 UI/UX Best Practices

### Mobile (React Native)

```jsx
<View style={styles.notificationSelector}>
  <Text style={styles.label}>Doğrulama kodunu nasıl almak istersiniz?</Text>
  
  <View style={styles.switchContainer}>
    <Text style={styles.channelText}>
      {notificationChannel === 'WHATSAPP' ? '📱 WhatsApp' : '📟 SMS'}
    </Text>
    
    <Switch
      value={notificationChannel === 'SMS'}
      onValueChange={(enabled) => 
        setNotificationChannel(enabled ? 'SMS' : 'WHATSAPP')
      }
      trackColor={{ false: '#25D366', true: '#007AFF' }}
    />
  </View>
  
  <Text style={styles.hint}>
    {notificationChannel === 'WHATSAPP' 
      ? 'WhatsApp ile daha hızlı ve ücretsiz'
      : 'SMS ile tüm telefonlara ulaşılabilir'}
  </Text>
</View>
```

### Web (React)

```jsx
<div className="notification-channel-selector">
  <label className="label">Doğrulama kodu gönderim tercihi</label>
  
  <div className="radio-group">
    <label className="radio-option">
      <input
        type="radio"
        value="EMAIL"
        checked={channel === 'EMAIL'}
        onChange={(e) => setChannel(e.target.value)}
      />
      <span className="icon">📧</span>
      <span>Email</span>
      <span className="badge">Önerilen</span>
    </label>
    
    <label className="radio-option">
      <input
        type="radio"
        value="WHATSAPP"
        checked={channel === 'WHATSAPP'}
        onChange={(e) => setChannel(e.target.value)}
      />
      <span className="icon">📱</span>
      <span>WhatsApp</span>
      <span className="badge">Hızlı</span>
    </label>
    
    <label className="radio-option">
      <input
        type="radio"
        value="SMS"
        checked={channel === 'SMS'}
        onChange={(e) => setChannel(e.target.value)}
      />
      <span className="icon">📟</span>
      <span>SMS</span>
    </label>
  </div>
</div>
```

---

## 🔒 Security Considerations

1. **Phone Validation:** Frontend'de phone number format validation (E.164)
2. **Channel Availability:** Backend'de tenant config check + platform fallback
3. **Rate Limiting:** Aynı telefona max 3 deneme / 15 dakika
4. **Idempotency:** Event ID ile duplicate gönderim önleme

---

## 📝 Validation Rules

### Frontend Validation

```javascript
// Phone number required for WhatsApp/SMS
if ((channel === 'WHATSAPP' || channel === 'SMS') && !phone) {
  throw new Error('Telefon numarası gerekli');
}

// Email required for Email channel
if (channel === 'EMAIL' && !email) {
  throw new Error('Email adresi gerekli');
}

// Phone format validation (E.164)
const phoneRegex = /^\+[1-9]\d{1,14}$/;
if (phone && !phoneRegex.test(phone)) {
  throw new Error('Geçersiz telefon formatı (örn: +905551234567)');
}
```

---

## 🧪 Testing

### Manual Test (Mobile)

1. Kayıt ekranında switch **kapalı** (WhatsApp) → Kayıt yap
2. WhatsApp'a kod geldi mi? ✅
3. Switch **açık** (SMS) → Kayıt yap
4. SMS'e kod geldi mi? ✅

### Manual Test (Web)

1. Email seç → Kayıt yap → Email'e kod geldi mi? ✅
2. WhatsApp seç → Kayıt yap → WhatsApp'a kod geldi mi? ✅
3. SMS seç → Kayıt yap → SMS'e kod geldi mi? ✅

---

## 🚀 Production Checklist

- [ ] Frontend: Switch/Radio buttons implemented
- [ ] Frontend: Phone validation (E.164)
- [ ] Backend: `preferredChannel` field added to registration request
- [ ] Backend: Kafka event updated with `preferredChannel`
- [ ] Backend: Notification service handles channel selection
- [ ] WhatsApp API credentials configured (tenant or platform)
- [ ] SMS API credentials configured (optional)
- [ ] Rate limiting tested (3 attempts / 15 min)
- [ ] Fallback pattern tested (WhatsApp → Email → SMS)

---

**Maintainer:** Fabric Management Team  
**Documentation Date:** 2025-10-15

