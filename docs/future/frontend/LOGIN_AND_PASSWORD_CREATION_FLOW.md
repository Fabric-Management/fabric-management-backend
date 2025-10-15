# 🔐 Login & Password Creation Flow - Frontend Guide

**Date:** 2025-10-14  
**Purpose:** Multi-step login with password creation for new users  
**Pattern:** Progressive disclosure (steps appear as needed)

---

## 📋 User Journey Overview

```
Login Page (Email/Phone + Password boxes)
   ↓
User enters email/phone → Check Contact
   ↓
┌─────────────────────────────────────┐
│ IF contact NOT in system:           │
│   → Show error message              │
│   → "Contact administrator"         │
└─────────────────────────────────────┘
   ↓
┌─────────────────────────────────────┐
│ IF contact EXISTS:                  │
│   ├─ HAS password?                  │
│   │    → Show password field        │
│   │    → Login button               │
│   │                                 │
│   └─ NO password?                   │
│        → Show "Create Password"     │
│        ├─ Verified?                 │
│        │    → Show password field   │
│        │                            │
│        └─ NOT verified?             │
│             → Send verification code│
│             → Show code input       │
│             → Then password field   │
└─────────────────────────────────────┘
```

---

## 🎨 Implementation

### STEP 1: Initial Login Form

```jsx
const LoginPage = () => {
  const [contactValue, setContactValue] = useState("");
  const [step, setStep] = useState("ENTER_CONTACT"); // State machine
  const [contactInfo, setContactInfo] = useState(null);

  // ENTER_CONTACT → CHECK_RESULT → VERIFY_CODE → CREATE_PASSWORD → LOGIN

  const handleCheckContact = async () => {
    const response = await axios.post(
      "http://localhost:8080/api/v1/users/auth/check-contact",
      { contactValue }
    );

    const data = response.data.data;
    setContactInfo(data);

    if (!data.exists) {
      // Contact not in system
      setStep("NOT_FOUND");
    } else if (data.hasPassword) {
      // User exists with password → show password field
      setStep("ENTER_PASSWORD");
    } else {
      // User exists but no password → create password flow
      setStep("CREATE_PASSWORD_INTRO");
    }
  };

  return (
    <LoginContainer>
      {step === "ENTER_CONTACT" && (
        <ContactInput
          value={contactValue}
          onChange={setContactValue}
          onSubmit={handleCheckContact}
        />
      )}

      {step === "NOT_FOUND" && (
        <ErrorMessage>
          This contact is not registered. Please contact your administrator or
          use your registered contact.
        </ErrorMessage>
      )}

      {step === "ENTER_PASSWORD" && (
        <PasswordLogin contactValue={contactValue} contactInfo={contactInfo} />
      )}

      {step === "CREATE_PASSWORD_INTRO" && (
        <CreatePasswordFlow
          contactValue={contactValue}
          userId={contactInfo.userId}
        />
      )}
    </LoginContainer>
  );
};
```

---

### STEP 2: Contact Input (Email or Phone)

**Endpoint:** `POST /api/v1/users/auth/check-contact`

**Request:**

```json
{
  "contactValue": "admin@acmetekstil.com"
}
```

**Response Scenarios:**

**A) Contact NOT in system:**

```json
{
  "success": true,
  "data": {
    "exists": false,
    "hasPassword": false,
    "userId": null,
    "message": "This contact is not registered. Please contact your administrator."
  }
}
```

**B) Contact exists, HAS password:**

```json
{
  "success": true,
  "data": {
    "exists": true,
    "hasPassword": true,
    "userId": "e9da5c36-c608-40e7-85d7-236919687d5b",
    "message": "Please enter your password"
  }
}
```

**C) Contact exists, NO password:**

```json
{
  "success": true,
  "data": {
    "exists": true,
    "hasPassword": false,
    "userId": "e9da5c36-c608-40e7-85d7-236919687d5b",
    "message": "Please create your password"
  }
}
```

**UI Component:**

```jsx
<ContactInputStep>
  <Input
    type="text"
    placeholder="Email or Phone (+905551234567)"
    value={contactValue}
    onChange={e => setContactValue(e.target.value)}
    autoFocus
  />
  <Button onClick={handleCheckContact}>Continue</Button>
  <Hint>Use any verified contact associated with your account</Hint>
</ContactInputStep>
```

---

### STEP 3A: Login (Has Password)

**Endpoint:** `POST /api/v1/users/auth/login`

```jsx
const PasswordLogin = ({ contactValue }) => {
  const [password, setPassword] = useState("");

  const handleLogin = async () => {
    try {
      const response = await axios.post(
        "http://localhost:8080/api/v1/users/auth/login",
        { contactValue, password }
      );

      const { token, refreshToken, userId, tenantId, role, firstName } =
        response.data.data;

      // Store auth
      localStorage.setItem("accessToken", token);
      localStorage.setItem("refreshToken", refreshToken);
      localStorage.setItem(
        "user",
        JSON.stringify({
          userId,
          tenantId,
          role,
        })
      );

      // Navigate
      navigate("/dashboard");
    } catch (error) {
      if (error.response?.status === 401) {
        toast.error("Incorrect password");
      }
    }
  };

  return (
    <div>
      <Label>{contactValue}</Label>
      <PasswordInput
        value={password}
        onChange={setPassword}
        onEnter={handleLogin}
      />
      <Button onClick={handleLogin}>Login</Button>
      <Link to="/forgot-password">Forgot password?</Link>
    </div>
  );
};
```

---

### STEP 3B: Create Password Flow (No Password)

**Sub-steps:**

```
1. Check if contact is verified
   ├─ Verified → Skip to password creation
   └─ Not verified → Send code → Verify → Then password
```

```jsx
const CreatePasswordFlow = ({ contactValue, userId }) => {
  const [subStep, setSubStep] = useState("CHECK_VERIFICATION");
  const [contactId, setContactId] = useState(null);
  const [isVerified, setIsVerified] = useState(false);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");

  // Check verification status
  useEffect(() => {
    const checkVerification = async () => {
      const response = await axios.get(
        `http://localhost:8080/api/v1/contacts/find-by-value?contactValue=${contactValue}`
      );

      const contact = response.data.data;
      setContactId(contact.id);
      setIsVerified(contact.isVerified);

      if (contact.isVerified) {
        setSubStep("CREATE_PASSWORD");
      } else {
        setSubStep("SEND_VERIFICATION");
      }
    };

    checkVerification();
  }, [contactValue]);

  const handleSendCode = async () => {
    await axios.post(
      `http://localhost:8080/api/v1/contacts/${contactId}/send-verification`
    );
    toast.success("Verification code sent!");
    setSubStep("ENTER_CODE");
  };

  const handleVerifyCode = async () => {
    try {
      await axios.put(
        `http://localhost:8080/api/v1/contacts/${contactId}/verify?code=${code}`
      );
      toast.success("Contact verified!");
      setIsVerified(true);
      setSubStep("CREATE_PASSWORD");
    } catch (error) {
      toast.error("Invalid code");
    }
  };

  const handleCreatePassword = async () => {
    try {
      // Setup password
      await axios.post(
        "http://localhost:8080/api/v1/users/auth/setup-password",
        { contactValue, password }
      );

      // Auto-login
      const loginResponse = await axios.post(
        "http://localhost:8080/api/v1/users/auth/login",
        { contactValue, password }
      );

      // Store & redirect (same as STEP 3A)
      // ...
    } catch (error) {
      toast.error("Failed to create password");
    }
  };

  return (
    <div>
      {subStep === "SEND_VERIFICATION" && (
        <VerificationRequired>
          <p>This contact needs verification first</p>
          <Button onClick={handleSendCode}>Send Verification Code</Button>
        </VerificationRequired>
      )}

      {subStep === "ENTER_CODE" && (
        <CodeVerification>
          <p>Enter code sent to {contactValue}</p>
          <CodeInput value={code} onChange={setCode} length={6} />
          <Button onClick={handleVerifyCode}>Verify</Button>
          <Link onClick={handleSendCode}>Resend Code</Link>
        </CodeVerification>
      )}

      {subStep === "CREATE_PASSWORD" && (
        <PasswordCreation>
          <p>Create your password</p>
          <PasswordInput value={password} onChange={setPassword} />
          <PasswordStrengthMeter password={password} />
          <Button
            onClick={handleCreatePassword}
            disabled={!isPasswordValid(password)}>
            Create Password & Login
          </Button>
        </PasswordCreation>
      )}
    </div>
  );
};
```

---

## 🔄 Complete State Machine

```javascript
const LOGIN_STATES = {
  // Initial
  ENTER_CONTACT: "ENTER_CONTACT",

  // After check-contact
  NOT_FOUND: "NOT_FOUND",
  ENTER_PASSWORD: "ENTER_PASSWORD", // Has password

  // Create password flow
  CREATE_PASSWORD_INTRO: "CREATE_PASSWORD_INTRO",
  SEND_VERIFICATION: "SEND_VERIFICATION", // Unverified contact
  ENTER_CODE: "ENTER_CODE",
  CREATE_PASSWORD: "CREATE_PASSWORD",

  // Final
  LOGGED_IN: "LOGGED_IN",
};
```

---

## 🛡️ Security Rules

### Corporate Email Restriction

**Applies ONLY to:** Tenant registration (company creation)

```javascript
// Tenant onboarding
if (isOnboarding) {
  validateCorporateEmail(email); // ✅ Required
}

// Regular login / password creation
// ❌ NO restriction - any verified contact allowed
```

### Contact Verification

**Rule:** Contact MUST be verified before password setup

**Flow for unverified contact:**

```
User enters unverified email/phone
   ↓
System sends 6-digit code
   ↓
User enters code
   ↓
Contact verified
   ↓
Password creation allowed
```

---

## 📱 UI/UX Patterns

### Progressive Form (Not separate pages)

```jsx
<LoginForm>
  {/* Always visible */}
  <ContactInput value={contactValue} />

  {/* Appears after check-contact */}
  {step >= "ENTER_CODE" && (
    <Collapse>
      <CodeInput value={code} />
    </Collapse>
  )}

  {/* Appears after verification */}
  {step >= "CREATE_PASSWORD" && (
    <Collapse>
      <PasswordInput value={password} />
      <PasswordStrength />
    </Collapse>
  )}

  {/* Dynamic button */}
  <Button onClick={getCurrentAction()}>{getButtonText()}</Button>
</LoginForm>
```

### Button Text Changes

```javascript
const getButtonText = () => {
  switch (step) {
    case "ENTER_CONTACT":
      return "Continue";
    case "SEND_VERIFICATION":
      return "Send Code";
    case "ENTER_CODE":
      return "Verify";
    case "CREATE_PASSWORD":
      return "Create Password & Login";
    case "ENTER_PASSWORD":
      return "Login";
  }
};
```

---

## 🔍 Backend Validation Summary

### Contact Check (`/check-contact`)

```
✅ Contact in system? → exists: true/false
✅ Has password? → hasPassword: true/false
✅ Returns userId for next steps
✅ Timing-attack protection (min 200ms response)
```

### Password Setup (`/setup-password`)

```
✅ Contact MUST be verified
✅ Contact MUST exist in system
✅ Password MUST NOT already exist
✅ Password strength validation (regex)
✅ Status updated: PENDING_VERIFICATION → ACTIVE
```

### Verification (`/contacts/{id}/verify`)

```
✅ Public endpoint (no auth)
✅ 6-digit code validation
✅ 15-minute expiration
✅ Brute-force protection (max attempts)
```

---

## 📊 Example Scenarios

### Scenario A: New User (Unverified Contact)

```
TENANT_ADMIN creates user with email: newuser@acme.com
   ↓
User visits login page
   ↓
Enters: newuser@acme.com
   ↓
Response: exists=true, hasPassword=false, userId=xxx
   ↓
UI shows: "Create Your Password" section
   ↓
Backend checks: Contact verified? NO
   ↓
UI automatically sends verification code
   ↓
User enters 6-digit code
   ↓
Contact verified ✅
   ↓
UI shows password creation field
   ↓
User creates password
   ↓
Auto-login → Dashboard
```

### Scenario B: Existing User (Verified, Has Password)

```
User enters: admin@acme.com
   ↓
Response: exists=true, hasPassword=true, userId=xxx
   ↓
UI shows: Password field
   ↓
User enters password
   ↓
Login → Dashboard
```

### Scenario C: User with Personal Email (Verified)

```
User added personal email: john@gmail.com (verified)
   ↓
User enters: john@gmail.com
   ↓
Response: exists=true, hasPassword=true (same user), userId=xxx
   ↓
UI shows: Password field
   ↓
Login with SAME password → Dashboard
```

### Scenario D: Forgot Password (Verified Contact)

```
User clicks "Forgot Password?"
   ↓
Enters verified contact: admin@acme.com
   ↓
Verification code sent
   ↓
User enters code
   ↓
Create new password
   ↓
Password updated
   ↓
Auto-login → Dashboard
```

---

## 🚨 Important Backend Behavior

### Corporate Email Restriction

```
❌ ONLY for: /api/v1/public/onboarding/register (tenant creation)
✅ NOT for: /setup-password (any verified contact allowed)
✅ NOT for: /login (any verified contact allowed)
```

**Why:**

- Tenant creation = Company registration → Corporate email proves legitimacy
- Login/Password = User already in system → Any verified contact OK (convenience)

### Password Per User (Not Per Contact)

```
User has 1 password for ALL contacts:
- Corporate email: admin@acme.com
- Personal email: john@gmail.com
- Phone: +905551234567

All use SAME password!
```

---

## 🎯 API Endpoints Summary

| Step                      | Endpoint                                      | Auth   | Method |
| ------------------------- | --------------------------------------------- | ------ | ------ |
| 1. Check contact          | `/api/v1/users/auth/check-contact`            | Public | POST   |
| 2. Get contact details    | `/api/v1/contacts/find-by-value`              | Public | GET    |
| 3. Send verification code | `/api/v1/contacts/public/resend-verification` | Public | POST   |
| 4. Verify code            | `/api/v1/contacts/{id}/verify`                | Public | PUT    |
| 5. Create password        | `/api/v1/users/auth/setup-password`           | Public | POST   |
| 6. Login                  | `/api/v1/users/auth/login`                    | Public | POST   |

**✅ All endpoints are now public - no authentication required for onboarding flow**

**Security:** Rate limiting (production) + code expiration (15min) + max attempts protection

---

## 💡 Frontend Best Practices

### 1. State Persistence

```javascript
// Save state to sessionStorage (survive page refresh)
useEffect(() => {
  sessionStorage.setItem(
    "loginState",
    JSON.stringify({
      step,
      contactValue,
      contactId,
      isVerified,
    })
  );
}, [step, contactValue, contactId, isVerified]);
```

### 2. Auto-focus Next Field

```javascript
// When code input appears, auto-focus
useEffect(() => {
  if (step === "ENTER_CODE") {
    codeInputRef.current?.focus();
  }
}, [step]);
```

### 3. Code Input UX

```jsx
// 6 separate boxes (better UX than single input)
<CodeInputBoxes>
  {[0, 1, 2, 3, 4, 5].map(i => (
    <Input
      key={i}
      maxLength={1}
      value={code[i] || ""}
      onChange={e => handleCodeChange(i, e.target.value)}
      onKeyDown={e => handleKeyDown(i, e)}
      autoFocus={i === 0}
    />
  ))}
</CodeInputBoxes>
```

### 4. Password Strength Indicator

```javascript
const getStrengthColor = strength => {
  if (strength < 2) return "red";
  if (strength < 4) return "orange";
  return "green";
};

<StrengthBar color={getStrengthColor(strength)}>
  {strength < 2 && "Weak"}
  {strength >= 2 && strength < 4 && "Medium"}
  {strength >= 4 && "Strong"}
</StrengthBar>;
```

---

## 🧪 Testing Scenarios

### Test Case 1: Brand New User

```
1. TENANT_ADMIN creates user: newuser@acme.com
2. User navigates to login page
3. Enters: newuser@acme.com → check-contact
4. System: exists=true, hasPassword=false
5. System auto-checks: isVerified=false
6. Button appears: "Send Verification Code"
7. User clicks → Code sent
8. User enters code → Contact verified
9. Password field appears
10. User creates password → Auto-login
11. Dashboard loads
```

### Test Case 2: Existing User, New Contact

```
1. User (has password) adds personal email: john@gmail.com
2. Personal email verified via settings
3. User logs out
4. User tries login with: john@gmail.com
5. System: exists=true, hasPassword=true (SAME password!)
6. Password field appears
7. User enters password → Login successful
```

### Test Case 3: Unverified Contact + No Password

```
1. TENANT_ADMIN creates user: test@acme.com
2. User never verified email
3. User tries login: test@acme.com
4. System: exists=true, hasPassword=false
5. System checks: isVerified=false
6. Verification flow triggered
7. After verification → password creation
8. Login successful
```

---

## 🎨 Complete Component Example

```jsx
const SmartLoginForm = () => {
  const [state, setState] = useState({
    step: "CONTACT_INPUT",
    contactValue: "",
    contactId: null,
    userId: null,
    isVerified: false,
    hasPassword: false,
    code: "",
    password: "",
  });

  const handleContactSubmit = async () => {
    // Check contact
    const checkResponse = await checkContact(state.contactValue);

    if (!checkResponse.exists) {
      setState(s => ({ ...s, step: "NOT_FOUND" }));
      return;
    }

    setState(s => ({
      ...s,
      userId: checkResponse.userId,
      hasPassword: checkResponse.hasPassword,
    }));

    if (checkResponse.hasPassword) {
      // Has password → show password field
      setState(s => ({ ...s, step: "PASSWORD_INPUT" }));
    } else {
      // No password → check verification
      const contactDetails = await getContactDetails(state.contactValue);

      if (contactDetails.isVerified) {
        setState(s => ({
          ...s,
          step: "CREATE_PASSWORD",
          contactId: contactDetails.id,
          isVerified: true,
        }));
      } else {
        setState(s => ({
          ...s,
          step: "NEED_VERIFICATION",
          contactId: contactDetails.id,
          isVerified: false,
        }));
      }
    }
  };

  const handleSendCode = async () => {
    await sendVerificationCode(state.contactId);
    setState(s => ({ ...s, step: "VERIFY_CODE" }));
  };

  const handleVerifyCode = async () => {
    await verifyContact(state.contactId, state.code);
    setState(s => ({ ...s, step: "CREATE_PASSWORD", isVerified: true }));
  };

  const handleCreatePassword = async () => {
    await setupPassword(state.contactValue, state.password);
    await doLogin(state.contactValue, state.password);
  };

  const handleLogin = async () => {
    await doLogin(state.contactValue, state.password);
  };

  return (
    <Card>
      <AnimatedSteps currentStep={state.step}>
        {/* Step 1: Always visible */}
        <ContactInputField
          value={state.contactValue}
          onChange={v => setState(s => ({ ...s, contactValue: v }))}
          onSubmit={handleContactSubmit}
          disabled={state.step !== "CONTACT_INPUT"}
        />

        {/* Step 2: Verification (if needed) */}
        <Collapse in={state.step === "NEED_VERIFICATION"}>
          <Alert>Contact needs verification</Alert>
          <Button onClick={handleSendCode}>Send Code</Button>
        </Collapse>

        <Collapse in={state.step === "VERIFY_CODE"}>
          <CodeInput
            value={state.code}
            onChange={v => setState(s => ({ ...s, code: v }))}
          />
          <Button onClick={handleVerifyCode}>Verify</Button>
        </Collapse>

        {/* Step 3: Password (create or enter) */}
        <Collapse in={state.step === "CREATE_PASSWORD"}>
          <PasswordInput
            value={state.password}
            onChange={v => setState(s => ({ ...s, password: v }))}
            placeholder="Create your password"
          />
          <PasswordStrength password={state.password} />
          <Button onClick={handleCreatePassword}>Create & Login</Button>
        </Collapse>

        <Collapse in={state.step === "PASSWORD_INPUT"}>
          <PasswordInput
            value={state.password}
            onChange={v => setState(s => ({ ...s, password: v }))}
            placeholder="Enter your password"
          />
          <Button onClick={handleLogin}>Login</Button>
          <Link to="/forgot-password">Forgot password?</Link>
        </Collapse>

        {/* Error state */}
        <Collapse in={state.step === "NOT_FOUND"}>
          <Alert severity="error">
            Contact not found. Please contact your administrator.
          </Alert>
        </Collapse>
      </AnimatedSteps>
    </Card>
  );
};
```

---

**Version:** 1.0  
**Last Updated:** 2025-10-14  
**Status:** ✅ Backend 100% Ready - All endpoints implemented
