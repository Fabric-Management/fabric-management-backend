# 🚀 Tenant Onboarding - Frontend Implementation Guide

**Date:** 2025-10-14  
**Status:** 🎯 Backend Ready - Awaiting Frontend Implementation  
**Backend Endpoints:** ✅ Fully Tested & Working

---

## 📋 User Flow Overview

```
Step 1: Registration Form
   ↓
Step 2: Email Verification (6-digit code)
   ↓
Step 3: Password Setup
   ↓
Step 4: Auto-login & Dashboard Redirect
```

---

## 🎨 Step-by-Step Implementation

### STEP 1: Registration Form

**Endpoint:** `POST /api/v1/public/onboarding/register`

**Request Body:**

```json
{
  "companyName": "Acme Tekstil A.Ş.",
  "legalName": "Acme Tekstil Anonim Şirketi",
  "taxId": "1234567890",
  "registrationNumber": "REG-2025-001",
  "companyType": "CORPORATION",
  "industry": "MANUFACTURING",
  "description": "Textile manufacturing company",
  "website": "https://acmetekstil.com",
  "addressLine1": "Organize Sanayi Bölgesi 5. Cadde No:42",
  "addressLine2": "A Blok Kat:3",
  "city": "İstanbul",
  "district": "Esenyurt",
  "postalCode": "34520",
  "country": "Turkey",
  "firstName": "Ahmet",
  "lastName": "Yılmaz",
  "email": "admin@acmetekstil.com",
  "phone": "+905551234567"
}
```

**Response (Success):**

```json
{
  "success": true,
  "message": "Tenant registered successfully",
  "data": {
    "companyId": "992d52d6-9826-4138-a5cd-95807734dc44",
    "userId": "e9da5c36-c608-40e7-85d7-236919687d5b",
    "email": "admin@acmetekstil.com",
    "message": "Registration successful",
    "nextStep": "Please check your email to verify your account"
  },
  "timestamp": "2025-10-14T16:47:45.357780057"
}
```

**UI Actions:**

```javascript
// Save these values to state/localStorage
const { companyId, userId, email } = response.data;

// Navigate to verification page
navigate("/verify", {
  state: { userId, email },
});

// Show success message
toast.success(
  "Registration successful! Check your email for verification code."
);
```

**Form Fields:**

| Field                | Type   | Required | Validation                           |
| -------------------- | ------ | -------- | ------------------------------------ |
| `companyName`        | text   | ✅       | Min 2 chars                          |
| `legalName`          | text   | ✅       | Min 2 chars                          |
| `taxId`              | text   | ✅       | Numeric, 10 chars                    |
| `registrationNumber` | text   | ✅       | Min 2 chars                          |
| `companyType`        | select | ✅       | CORPORATION, LLC, SOLE_PROPRIETOR    |
| `industry`           | select | ✅       | MANUFACTURING, RETAIL, etc.          |
| `website`            | url    | ⚠️       | Valid URL format                     |
| `email`              | email  | ✅       | Corporate email (no Gmail/Yahoo)     |
| `phone`              | tel    | ✅       | +90 format                           |
| `firstName`          | text   | ✅       | Admin's first name                   |
| `lastName`           | text   | ✅       | Admin's last name                    |
| Address fields       | text   | ✅       | City, district, postal code, country |

**Error Handling:**

```javascript
// Duplicate company (400)
{
  "success": false,
  "errorCode": "TENANT_REGISTRATION_ERROR",
  "message": "Company with tax ID '1234567890' is already registered..."
}

// Invalid corporate email (400)
{
  "message": "Please use your corporate email address. Personal emails (Gmail, Yahoo, etc.) are not allowed for company registration."
}
```

---

### STEP 2: Email Verification

**Scenario:** User receives email with 6-digit code

**UI Page:** Verification Code Input

```jsx
<VerificationForm>
  <Input
    name="code"
    placeholder="Enter 6-digit code"
    maxLength={6}
    autoComplete="one-time-code"
  />
  <Timer countdown={15} /> {/* Code expires in 15 minutes */}
  <Button>Verify & Continue</Button>
  <Link>Resend Code</Link>
</VerificationForm>
```

**Endpoint:** `PUT /api/v1/contacts/{contactId}/verify?code=123456`

**Parameters:**

- `contactId`: From registration response (extract from userId via contact service)
- `code`: User-entered 6-digit code

**Request:**

```javascript
// Option 1: If you have contactId from registration
PUT /api/v1/contacts/e9da5c36-c608-40e7-85d7-236919687d5b/verify?code=123456

// Option 2: Find contactId from email first
// GET /api/v1/contacts/find-by-value?contactValue=admin@acmetekstil.com
// Then verify
```

**Response (Success):**

```json
{
  "success": true,
  "message": "Contact verified successfully",
  "data": null,
  "timestamp": "2025-10-14T16:50:00.000000000"
}
```

**UI Actions:**

```javascript
// On success
toast.success("Email verified successfully!");
navigate("/setup-password", {
  state: { email },
});
```

**Resend Code:**

```javascript
// Endpoint: POST /api/v1/contacts/{contactId}/send-verification
// This is INTERNAL endpoint - needs refactoring to public!
```

---

### STEP 3: Password Setup

**Endpoint:** `POST /api/v1/users/auth/setup-password`

**Request Body:**

```json
{
  "contactValue": "admin@acmetekstil.com",
  "password": "SecurePass123!"
}
```

**Password Rules:**

- Minimum 8 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 number
- At least 1 special character (@$!%\*?&)

**Validation Regex:**

```javascript
const passwordRegex =
  /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
```

**Response (Success):**

```json
{
  "success": true,
  "message": "Password created successfully",
  "data": null,
  "timestamp": "2025-10-14T16:52:00.000000000"
}
```

**UI Component:**

```jsx
<PasswordSetupForm>
  <Input type="password" name="password" placeholder="Create password" />
  <PasswordStrengthMeter password={password} />
  <PasswordRules />
  <Button disabled={!isPasswordValid}>Set Password & Login</Button>
</PasswordSetupForm>
```

---

### STEP 4: Auto-login

**After password setup, automatically call login:**

**Endpoint:** `POST /api/v1/users/auth/login`

**Request Body:**

```json
{
  "contactValue": "admin@acmetekstil.com",
  "password": "SecurePass123!"
}
```

**Response (Success):**

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "userId": "e9da5c36-c608-40e7-85d7-236919687d5b",
    "tenantId": "992d52d6-9826-4138-a5cd-95807734dc44",
    "email": "admin@acmetekstil.com",
    "firstName": "Ahmet",
    "lastName": "Yılmaz",
    "role": "TENANT_ADMIN",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresAt": "2025-10-14T17:47:45.357780057"
  },
  "timestamp": "2025-10-14T16:52:30.000000000"
}
```

**UI Actions:**

```javascript
// Store JWT token
localStorage.setItem("accessToken", response.data.token);
localStorage.setItem("refreshToken", response.data.refreshToken);

// Store user info
localStorage.setItem(
  "user",
  JSON.stringify({
    userId: response.data.userId,
    tenantId: response.data.tenantId,
    role: response.data.role,
    email: response.data.email,
  })
);

// Redirect to dashboard
navigate("/dashboard");

// Welcome message
toast.success(`Welcome, ${response.data.firstName}! 🎉`);
```

---

## 🔐 Security Considerations

### Public Endpoints (No JWT Required)

```
✅ POST /api/v1/public/onboarding/register
✅ PUT  /api/v1/contacts/{contactId}/verify?code={code}
✅ POST /api/v1/users/auth/setup-password
✅ POST /api/v1/users/auth/login
```

### Authentication

- Registration → No auth needed
- Verification → Code validation (contactId + 6-digit code)
- Setup password → Verified contact required (checked in backend)
- Login → Email + password

### Rate Limiting

**Production:**

```
GATEWAY_RATE_LIMIT_ENABLED=true

- Onboarding: 5 requests/minute per IP
- Login: 5 requests/minute per IP
- Setup password: 3 requests/minute per IP
```

**Development:**

```
GATEWAY_RATE_LIMIT_ENABLED=false  # Default
```

---

## 📱 Complete Flow Example (React/Vue)

### Registration Page

```jsx
const RegisterPage = () => {
  const [formData, setFormData] = useState({});
  const navigate = useNavigate();

  const handleSubmit = async e => {
    e.preventDefault();

    try {
      const response = await axios.post(
        "http://localhost:8080/api/v1/public/onboarding/register",
        formData
      );

      // Save response data
      const { companyId, userId, email } = response.data.data;

      // Navigate to verification
      navigate("/verify", {
        state: {
          userId,
          email,
          companyId,
        },
      });
    } catch (error) {
      if (error.response?.data?.errorCode === "TENANT_REGISTRATION_ERROR") {
        toast.error(error.response.data.message);
      }
    }
  };

  return <RegistrationForm onSubmit={handleSubmit} />;
};
```

### Verification Page

```jsx
const VerificationPage = () => {
  const { state } = useLocation();
  const { email } = state;
  const [code, setCode] = useState("");
  const [contactId, setContactId] = useState(null);
  const navigate = useNavigate();

  // Get contactId from email
  useEffect(() => {
    const fetchContactId = async () => {
      const response = await axios.get(
        `http://localhost:8080/api/v1/contacts/find-by-value?contactValue=${email}`
      );
      setContactId(response.data.data.id);
    };
    fetchContactId();
  }, [email]);

  const handleVerify = async () => {
    try {
      await axios.put(
        `http://localhost:8080/api/v1/contacts/${contactId}/verify?code=${code}`
      );

      toast.success("Email verified! ✅");
      navigate("/setup-password", { state: { email } });
    } catch (error) {
      toast.error("Invalid or expired code");
    }
  };

  return (
    <div>
      <h2>Verify Your Email</h2>
      <p>We sent a 6-digit code to {email}</p>
      <CodeInput value={code} onChange={setCode} length={6} />
      <Button onClick={handleVerify}>Verify & Continue</Button>
    </div>
  );
};
```

### Password Setup Page

```jsx
const SetupPasswordPage = () => {
  const { state } = useLocation();
  const { email } = state;
  const [password, setPassword] = useState("");
  const navigate = useNavigate();

  const handleSetupPassword = async () => {
    try {
      // Step 1: Setup password
      await axios.post(
        "http://localhost:8080/api/v1/users/auth/setup-password",
        { contactValue: email, password }
      );

      toast.success("Password created! Logging you in...");

      // Step 2: Auto-login
      const loginResponse = await axios.post(
        "http://localhost:8080/api/v1/users/auth/login",
        { contactValue: email, password }
      );

      // Step 3: Store JWT & user info
      const { token, refreshToken, userId, tenantId, role, firstName } =
        loginResponse.data.data;

      localStorage.setItem("accessToken", token);
      localStorage.setItem("refreshToken", refreshToken);
      localStorage.setItem(
        "user",
        JSON.stringify({
          userId,
          tenantId,
          role,
          email,
          firstName,
        })
      );

      // Step 4: Navigate to dashboard
      navigate("/dashboard");
      toast.success(`Welcome, ${firstName}! 🎉`);
    } catch (error) {
      toast.error("Failed to setup password. Please try again.");
    }
  };

  return (
    <div>
      <h2>Create Your Password</h2>
      <PasswordInput value={password} onChange={setPassword} />
      <PasswordStrengthMeter password={password} />
      <PasswordRules />
      <Button
        onClick={handleSetupPassword}
        disabled={!isPasswordValid(password)}>
        Set Password & Login
      </Button>
    </div>
  );
};
```

---

## 🛡️ Error Handling

### Registration Errors

```javascript
const handleRegistrationError = error => {
  const { errorCode, message } = error.response.data;

  switch (errorCode) {
    case "TENANT_REGISTRATION_ERROR":
      // Duplicate company, invalid data, etc.
      toast.error(message);
      break;

    case "SERVICE_UNAVAILABLE":
      toast.error("Service temporarily unavailable. Please try again.");
      break;

    default:
      toast.error("Registration failed. Please contact support.");
  }
};
```

### Verification Errors

```javascript
// Invalid code
{
  "success": false,
  "message": "Invalid verification code",
  "errorCode": "INVALID_VERIFICATION_CODE"
}

// Expired code
{
  "success": false,
  "message": "Verification code has expired",
  "errorCode": "VERIFICATION_CODE_EXPIRED"
}

// Max attempts exceeded
{
  "success": false,
  "message": "Maximum verification attempts exceeded",
  "errorCode": "MAX_VERIFICATION_ATTEMPTS"
}
```

### Password Setup Errors

```javascript
// Contact not verified
{
  "success": false,
  "message": "Contact must be verified before setting password",
  "errorCode": "CONTACT_NOT_VERIFIED"
}

// Password already set
{
  "success": false,
  "message": "Password is already set for this contact",
  "errorCode": "PASSWORD_ALREADY_SET"
}
```

---

## 📊 State Management

### React Context Example

```jsx
const OnboardingContext = createContext();

export const OnboardingProvider = ({ children }) => {
  const [state, setState] = useState({
    step: 1, // 1: Register, 2: Verify, 3: Password
    companyId: null,
    userId: null,
    email: null,
    contactId: null,
  });

  const nextStep = () => setState(s => ({ ...s, step: s.step + 1 }));

  const setRegistrationData = data => {
    setState(s => ({
      ...s,
      companyId: data.companyId,
      userId: data.userId,
      email: data.email,
    }));
  };

  return (
    <OnboardingContext.Provider
      value={{ state, nextStep, setRegistrationData }}>
      {children}
    </OnboardingContext.Provider>
  );
};
```

---

## 🎯 UX Best Practices

### Progress Indicator

```jsx
<ProgressSteps>
  <Step active={step === 1} completed={step > 1}>
    1. Company Info
  </Step>
  <Step active={step === 2} completed={step > 2}>
    2. Verify Email
  </Step>
  <Step active={step === 3} completed={step > 3}>
    3. Create Password
  </Step>
  <Step active={step === 4}>4. Complete</Step>
</ProgressSteps>
```

### Form Validation

```javascript
// Real-time validation
const validateEmail = email => {
  const corporateEmails = ["gmail.com", "yahoo.com", "hotmail.com"];
  const domain = email.split("@")[1];

  if (corporateEmails.includes(domain)) {
    return "Please use your corporate email address";
  }
  return null;
};

// Password strength
const getPasswordStrength = password => {
  let strength = 0;
  if (password.length >= 8) strength++;
  if (/[A-Z]/.test(password)) strength++;
  if (/[a-z]/.test(password)) strength++;
  if (/[0-9]/.test(password)) strength++;
  if (/[@$!%*?&]/.test(password)) strength++;

  return strength; // 0-5
};
```

### Loading States

```jsx
const [isLoading, setIsLoading] = useState(false);

<Button disabled={isLoading}>
  {isLoading ? <Spinner /> : "Register Company"}
</Button>;
```

---

## 🔄 Resend Verification Code

**⚠️ TODO:** Currently `/send-verification` is internal endpoint

**Temporary Solution:**

User can request new registration if code expired (start from Step 1).

**Future Solution:**

Create public endpoint: `POST /api/v1/contacts/resend-verification`

```json
{
  "contactValue": "admin@acmetekstil.com"
}
```

---

## 🌐 API Gateway Configuration

All endpoints go through API Gateway (`http://localhost:8080`)

**Headers (Auto-added by Gateway):**

```
x-correlation-id: Generated by gateway
x-request-id: Generated by gateway
```

**CORS:**

```javascript
// Allowed origins (configure in backend)
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200

// Frontend axios config
axios.defaults.baseURL = 'http://localhost:8080';
axios.defaults.withCredentials = true;
```

---

## ✅ Testing Checklist

### Happy Path

- [ ] Fill registration form with valid corporate email
- [ ] Submit → Success response with companyId + userId
- [ ] Check email for 6-digit code
- [ ] Enter code → Contact verified
- [ ] Create password (meets requirements)
- [ ] Auto-login successful
- [ ] Dashboard loads with user info

### Error Cases

- [ ] Duplicate company (tax ID exists) → Error message shown
- [ ] Personal email (Gmail) → Error message shown
- [ ] Invalid verification code → Error message, can retry
- [ ] Expired code (>15 min) → Error message, can resend
- [ ] Weak password → Validation error, cannot submit
- [ ] Network error → Graceful error handling

---

## 📞 Backend Team Contact

**Questions about:**

- API endpoints → Check Postman collections in `/postman/`
- Error codes → See `/docs/api/README.md`
- Authentication → See `/docs/SECURITY.md`
- Architecture → See `/docs/ARCHITECTURE.md`

---

**Version:** 1.0  
**Last Updated:** 2025-10-14  
**Status:** Backend Ready ✅ - Frontend Pending ⏳
