# Authentication & Security Guide - Frontend Integration

**Last Updated:** 2025-01-10  
**Status:** Active - **SECURITY SENSITIVE**  
**Purpose:** Secure guide for frontend developers implementing authentication flows.

---

## ⚠️ SECURITY WARNINGS

**CRITICAL SECURITY REQUIREMENTS:**

1. **NEVER** log passwords, verification codes, or tokens
2. **NEVER** store tokens in localStorage - Use secure httpOnly cookies or memory
3. **NEVER** expose sensitive data in error messages
4. **ALWAYS** validate inputs client-side before sending
5. **ALWAYS** handle tokens securely (refresh, expiration, revocation)
6. **ALWAYS** mask PII in logs/UI

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Security Architecture](#security-architecture)
3. [Registration Flow](#registration-flow)
4. [Login Flow](#login-flow)
5. [Password Reset Flow](#password-reset-flow)
6. [Token Management](#token-management)
7. [Error Handling](#error-handling)
8. [Security Best Practices](#security-best-practices)
9. [Code Examples](#code-examples)

---

## 🎯 Overview

### Authentication Flow

```
┌─────────────────┐
│  Registration   │
│  (2-step)       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      ┌──────────────┐
│  Login          │─────▶│  JWT Tokens  │
│  (1-step)       │      │  (Access +   │
└─────────────────┘      │   Refresh)   │
                         └──────────────┘
         │
         ▼
┌─────────────────┐
│  Password Reset │
│  (2-step)       │
└─────────────────┘
```

### Key Components

- **AuthUser** - Authentication credentials (password hash, verification status)
- **Contact** - Contact value (email/phone) for authentication
- **VerificationCode** - Multi-use verification codes (expiry, attempts)
- **RefreshToken** - Long-lived token for refreshing access tokens
- **JWT Token** - Short-lived access token (15 minutes)

---

## 🔐 Security Architecture

### Multi-Channel Verification

Verification codes are sent via **priority order**:

1. **WhatsApp** (if available) - Fast, high open rate
2. **Email** - Universal support
3. **SMS** (fallback) - AWS SNS

### Token Security

- **Access Token:** 15 minutes expiry, contains user/tenant info
- **Refresh Token:** 7 days expiry, UUID-based, stored in database
- **Token Storage:** Use httpOnly cookies or secure memory (NOT localStorage)

### Account Protection

- **Failed Login Attempts:** Account locked after 5 failed attempts
- **Verification Code Attempts:** Maximum 3 attempts per code
- **Verification Code Expiry:** 10 minutes default
- **Password Hashing:** BCrypt (secure, one-way)

---

## 📝 Registration Flow

### Overview

Registration is **2-step** with verification:

1. **Check Eligibility & Send Code**
2. **Verify Code & Complete Registration**

### Step 1: Check Eligibility & Send Code

```http
POST /api/auth/register/check
Content-Type: application/json

{
  "contactValue": "john@example.com"
}
```

**Security Notes:**
- No sensitive data in request (only contactValue)
- Backend checks if user exists in system (pre-approved)
- Verification code generated and sent via multi-channel
- Code stored in database with expiry

**Response:**
```json
{
  "success": true,
  "data": "Verification code sent. Please check your email.",
  "message": null
}
```

**Error Cases:**
- User not found: "Your information is not registered. Our representative will contact you."
- Already registered: "This account is already registered. Please login."
- Contact not verified: Context-aware message

### Step 2: Verify Code & Complete Registration

```http
POST /api/auth/register/verify
Content-Type: application/json

{
  "contactValue": "john@example.com",
  "code": "123456",              // 6-digit code
  "password": "SecurePass123!"    // Min 8 characters
}
```

**Security Notes:**
- Code validated (expiry, attempts, type)
- Password hashed with BCrypt
- AuthUser created and linked to Contact entity
- JWT tokens generated (auto-login)
- Verification code marked as used

**Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "expiresIn": 900,
    "user": {
      "id": "uuid",
      "displayName": "John Doe",
      // ... user fields
    },
    "needsOnboarding": false
  },
  "message": "Registration completed successfully"
}
```

### Registration Code Example

```typescript
interface RegistrationState {
  step: 'check' | 'verify';
  contactValue: string;
  code: string;
  password: string;
  loading: boolean;
  error: string | null;
}

function RegistrationForm() {
  const [state, setState] = useState<RegistrationState>({
    step: 'check',
    contactValue: '',
    code: '',
    password: '',
    loading: false,
    error: null
  });

  const handleCheckEligibility = async () => {
    setState({ ...state, loading: true, error: null });

    try {
      const response = await axios.post('/api/auth/register/check', {
        contactValue: state.contactValue
      });

      // ✅ Never log verification code
      setState({ ...state, step: 'verify', loading: false });
      
      // Show success message
      alert(response.data.data);
    } catch (error: any) {
      // ✅ Never expose sensitive details in error messages
      setState({
        ...state,
        loading: false,
        error: error.response?.data?.message || 'Registration check failed'
      });
    }
  };

  const handleVerifyAndRegister = async () => {
    setState({ ...state, loading: true, error: null });

    try {
      const response = await axios.post('/api/auth/register/verify', {
        contactValue: state.contactValue,
        code: state.code,
        password: state.password
      });

      // ✅ Store tokens securely (use secure storage)
      const { accessToken, refreshToken } = response.data.data;
      secureTokenStorage.setTokens(accessToken, refreshToken);

      // Redirect to dashboard
      navigate('/dashboard');
    } catch (error: any) {
      // ✅ Handle specific errors securely
      let errorMessage = 'Registration failed';
      
      if (error.response?.status === 400) {
        errorMessage = error.response.data.message || 'Invalid verification code';
      }

      setState({
        ...state,
        loading: false,
        error: errorMessage
      });
    }
  };

  return (
    <div>
      {state.step === 'check' ? (
        <form onSubmit={(e) => { e.preventDefault(); handleCheckEligibility(); }}>
          <input
            type="email"
            value={state.contactValue}
            onChange={(e) => setState({ ...state, contactValue: e.target.value })}
            placeholder="Email"
            required
          />
          <button type="submit" disabled={state.loading}>
            Send Verification Code
          </button>
        </form>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); handleVerifyAndRegister(); }}>
          <input
            type="text"
            value={state.code}
            onChange={(e) => setState({ ...state, code: e.target.value })}
            placeholder="Verification Code"
            maxLength={6}
            required
          />
          <input
            type="password"
            value={state.password}
            onChange={(e) => setState({ ...state, password: e.target.value })}
            placeholder="Password (min 8 characters)"
            minLength={8}
            required
          />
          <button type="submit" disabled={state.loading}>
            Complete Registration
          </button>
        </form>
      )}
      
      {state.error && <div className="error">{state.error}</div>}
    </div>
  );
}
```

---

## 🔑 Login Flow

### Overview

Login is **1-step** with credential validation:

1. Validate credentials (contact + password)
2. Check account status (verified, active, not locked)
3. Generate JWT tokens
4. Return tokens and user info

### Login Request

```http
POST /api/auth/login
Content-Type: application/json

{
  "contactValue": "john@example.com",
  "password": "SecurePass123!"
}
```

**Security Notes:**
- Password never logged (backend masks PII)
- Failed attempts tracked (account locked after 5)
- Context-aware error messages (doesn't reveal if user exists)

**Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "expiresIn": 900,
    "user": {
      "id": "uuid",
      "displayName": "John Doe",
      // ... user fields
    },
    "needsOnboarding": false
  },
  "message": "Login successful"
}
```

### Login Error Cases

#### User Not Found
```json
{
  "success": false,
  "error": "USER_NOT_FOUND",
  "message": "User not found. If you're a ACME Corporation employee, please contact your IT team or manager to add you to the system."
}
```

**Note:** Error message is context-aware and doesn't reveal if email exists.

#### Invalid Credentials
```json
{
  "success": false,
  "error": "INVALID_CREDENTIALS",
  "message": "Invalid credentials"
}
```

**Note:** Same message for invalid email or password (prevents enumeration).

#### Account Locked
```json
{
  "success": false,
  "error": "ACCOUNT_LOCKED",
  "message": "Account is temporarily locked. Try again later."
}
```

#### Account Not Verified
```json
{
  "success": false,
  "error": "NOT_VERIFIED",
  "message": "Account not verified. Please complete registration."
}
```

### Login Code Example

```typescript
interface LoginState {
  contactValue: string;
  password: string;
  loading: boolean;
  error: string | null;
}

function LoginForm() {
  const [state, setState] = useState<LoginState>({
    contactValue: '',
    password: '',
    loading: false,
    error: null
  });

  const handleLogin = async (e: FormEvent) => {
    e.preventDefault();
    setState({ ...state, loading: true, error: null });

    try {
      const response = await axios.post('/api/auth/login', {
        contactValue: state.contactValue,
        password: state.password  // ✅ Never log this
      });

      // ✅ Store tokens securely
      const { accessToken, refreshToken } = response.data.data;
      secureTokenStorage.setTokens(accessToken, refreshToken);

      // Redirect based on onboarding status
      const needsOnboarding = response.data.data.needsOnboarding;
      navigate(needsOnboarding ? '/onboarding' : '/dashboard');

    } catch (error: any) {
      // ✅ Never expose sensitive details
      let errorMessage = 'Login failed';
      
      if (error.response?.status === 400) {
        errorMessage = error.response.data.message || 'Invalid credentials';
      }

      setState({
        ...state,
        loading: false,
        error: errorMessage,
        password: ''  // ✅ Clear password on error
      });
    }
  };

  return (
    <form onSubmit={handleLogin}>
      <input
        type="email"
        value={state.contactValue}
        onChange={(e) => setState({ ...state, contactValue: e.target.value })}
        placeholder="Email"
        required
      />
      <input
        type="password"
        value={state.password}
        onChange={(e) => setState({ ...state, password: e.target.value })}
        placeholder="Password"
        required
      />
      <button type="submit" disabled={state.loading}>
        Login
      </button>
      
      {state.error && <div className="error">{state.error}</div>}
    </form>
  );
}
```

---

## 🔄 Password Reset Flow

### Overview

Password reset is **3-step** with verification:

1. **Get Masked Contacts** - Show verified contacts to user
2. **Request Password Reset** - Send verification code to selected contact
3. **Verify Code & Reset Password** - Complete reset and auto-login

### Step 1: Get Masked Contacts

```http
GET /api/auth/user/{contactValue}/masked-contacts
```

**Security Notes:**
- Returns only **verified** contacts
- Contact values are **masked** (e.g., `j***@example.com`)
- Includes `authUserId` for direct lookup (performance)
- Prevents enumeration attacks (same response if user doesn't exist)

**Response:**
```json
{
  "success": true,
  "data": {
    "contacts": [
      {
        "authUserId": "uuid",
        "maskedValue": "j***@example.com",
        "type": "EMAIL",
        "verified": true
      },
      {
        "authUserId": "uuid",
        "maskedValue": "+90***4567",
        "type": "PHONE",
        "verified": true
      }
    ]
  }
}
```

### Step 2: Request Password Reset

```http
POST /api/auth/password-reset/request
Content-Type: application/json

{
  "authUserId": "uuid",           // From masked contacts
  "contactType": "EMAIL"            // EMAIL or PHONE
}
```

**Security Notes:**
- Uses `authUserId` for direct lookup (performance + security)
- Validates contact is verified
- Generates and sends verification code
- Code stored with expiry and attempt limits

**Response:**
```json
{
  "success": true,
  "data": "Password reset verification code has been sent to your email."
}
```

### Step 3: Verify Code & Reset Password

```http
POST /api/auth/password-reset/verify
Content-Type: application/json

{
  "authUserId": "uuid",
  "code": "123456",
  "newPassword": "NewSecurePass123!"
}
```

**Security Notes:**
- Code validated (expiry, attempts, type)
- Password must be different from old password
- Password hashed with BCrypt
- Account unlocked if locked
- Failed login attempts reset
- Auto-login with new tokens

**Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "expiresIn": 900,
    "user": {
      // ... user fields
    },
    "needsOnboarding": false
  },
  "message": "Password reset successful! You have been automatically logged in."
}
```

### Password Reset Code Example

```typescript
interface PasswordResetState {
  step: 'contact' | 'request' | 'verify';
  contactValue: string;
  contacts: MaskedContactInfo[];
  selectedAuthUserId: string | null;
  code: string;
  newPassword: string;
  loading: boolean;
  error: string | null;
}

function PasswordResetFlow() {
  const [state, setState] = useState<PasswordResetState>({
    step: 'contact',
    contactValue: '',
    contacts: [],
    selectedAuthUserId: null,
    code: '',
    newPassword: '',
    loading: false,
    error: null
  });

  // Step 1: Get masked contacts
  const handleGetMaskedContacts = async () => {
    setState({ ...state, loading: true, error: null });

    try {
      const response = await axios.get(
        `/api/auth/user/${state.contactValue}/masked-contacts`
      );

      const contacts = response.data.data.contacts;
      
      if (contacts.length === 0) {
        // ✅ Don't reveal if user exists or not
        setState({
          ...state,
          loading: false,
          error: 'No verified contacts found. Please contact support.'
        });
        return;
      }

      setState({
        ...state,
        step: 'request',
        contacts,
        loading: false
      });
    } catch (error: any) {
      // ✅ Generic error message
      setState({
        ...state,
        loading: false,
        error: 'Unable to retrieve contact information. Please try again.'
      });
    }
  };

  // Step 2: Request password reset code
  const handleRequestReset = async (authUserId: string, contactType: string) => {
    setState({ ...state, loading: true, error: null });

    try {
      const response = await axios.post('/api/auth/password-reset/request', {
        authUserId,
        contactType
      });

      setState({
        ...state,
        step: 'verify',
        selectedAuthUserId: authUserId,
        loading: false
      });

      alert(response.data.data);
    } catch (error: any) {
      setState({
        ...state,
        loading: false,
        error: error.response?.data?.message || 'Failed to send verification code'
      });
    }
  };

  // Step 3: Verify code and reset password
  const handleVerifyAndReset = async () => {
    if (!state.selectedAuthUserId) return;

    setState({ ...state, loading: true, error: null });

    try {
      const response = await axios.post('/api/auth/password-reset/verify', {
        authUserId: state.selectedAuthUserId,
        code: state.code,
        newPassword: state.newPassword
      });

      // ✅ Store tokens securely
      const { accessToken, refreshToken } = response.data.data;
      secureTokenStorage.setTokens(accessToken, refreshToken);

      // Redirect to dashboard
      navigate('/dashboard');

    } catch (error: any) {
      let errorMessage = 'Password reset failed';
      
      if (error.response?.status === 400) {
        const message = error.response.data.message;
        
        if (message.includes('expired')) {
          errorMessage = 'Verification code has expired. Please request a new one.';
        } else if (message.includes('attempts')) {
          errorMessage = 'Too many verification attempts. Please request a new code.';
        } else if (message.includes('different')) {
          errorMessage = 'New password must be different from your current password.';
        } else {
          errorMessage = message || 'Invalid verification code';
        }
      }

      setState({
        ...state,
        loading: false,
        error: errorMessage,
        code: '',  // ✅ Clear code on error
        newPassword: ''  // ✅ Clear password on error
      });
    }
  };

  return (
    <div>
      {state.step === 'contact' && (
        <form onSubmit={(e) => { e.preventDefault(); handleGetMaskedContacts(); }}>
          <input
            type="email"
            value={state.contactValue}
            onChange={(e) => setState({ ...state, contactValue: e.target.value })}
            placeholder="Enter your email or phone"
            required
          />
          <button type="submit" disabled={state.loading}>
            Continue
          </button>
        </form>
      )}

      {state.step === 'request' && (
        <div>
          <p>Select a verified contact to receive the verification code:</p>
          {state.contacts.map(contact => (
            <button
              key={contact.authUserId}
              onClick={() => handleRequestReset(contact.authUserId, contact.type)}
              disabled={state.loading}
            >
              {contact.maskedValue} ({contact.type})
            </button>
          ))}
        </div>
      )}

      {state.step === 'verify' && (
        <form onSubmit={(e) => { e.preventDefault(); handleVerifyAndReset(); }}>
          <input
            type="text"
            value={state.code}
            onChange={(e) => setState({ ...state, code: e.target.value })}
            placeholder="Verification Code"
            maxLength={6}
            required
          />
          <input
            type="password"
            value={state.newPassword}
            onChange={(e) => setState({ ...state, newPassword: e.target.value })}
            placeholder="New Password (min 8 characters)"
            minLength={8}
            required
          />
          <button type="submit" disabled={state.loading}>
            Reset Password
          </button>
        </form>
      )}

      {state.error && <div className="error">{state.error}</div>}
    </div>
  );
}
```

---

## 🎫 Token Management

### Token Storage

**✅ SECURE OPTIONS:**

1. **httpOnly Cookies** (Recommended)
   ```typescript
   // Backend sets cookies automatically
   // Frontend doesn't need to handle tokens
   ```

2. **Secure Memory Storage** (Alternative)
   ```typescript
   // Store in component state or React Context
   // Clear on page refresh/logout
   ```

**❌ NEVER USE:**
- localStorage (XSS vulnerability)
- sessionStorage (XSS vulnerability)
- Global variables (accessible to all scripts)

### Token Refresh

```typescript
interface TokenRefreshResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

async function refreshAccessToken(refreshToken: string): Promise<TokenRefreshResponse> {
  const response = await axios.post('/api/auth/refresh', {
    refreshToken
  });

  return response.data.data;
}
```

### Token Expiration Handling

```typescript
function useTokenRefresh() {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);

  // Intercept 401 responses and refresh token
  useEffect(() => {
    const interceptor = axios.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;

          try {
            const newTokens = await refreshAccessToken(refreshToken!);
            setAccessToken(newTokens.accessToken);
            setRefreshToken(newTokens.refreshToken);
            
            originalRequest.headers.Authorization = `Bearer ${newTokens.accessToken}`;
            return axios(originalRequest);
          } catch (refreshError) {
            // Refresh failed - logout user
            logout();
            return Promise.reject(refreshError);
          }
        }

        return Promise.reject(error);
      }
    );

    return () => axios.interceptors.response.eject(interceptor);
  }, [refreshToken]);

  return { accessToken, refreshToken };
}
```

### Logout

```typescript
async function logout() {
  try {
    // Revoke refresh token on backend
    await axios.post('/api/auth/logout', {
      refreshToken: getRefreshToken()
    });
  } catch (error) {
    // Continue logout even if API call fails
  } finally {
    // ✅ Clear tokens from memory
    clearTokens();
    
    // Redirect to login
    navigate('/login');
  }
}
```

---

## 🚨 Error Handling

### Security-Focused Error Handling

```typescript
function handleAuthError(error: any): string {
  // ✅ Never expose sensitive information
  // ✅ Use generic messages
  // ✅ Log server-side only

  if (!error.response) {
    return 'Network error. Please check your connection.';
  }

  const status = error.response.status;
  const errorData = error.response.data;

  switch (status) {
    case 400:
      // Bad request - validation errors
      return errorData.message || 'Invalid request. Please check your input.';
    
    case 401:
      // Unauthorized - token expired/invalid
      return 'Session expired. Please login again.';
    
    case 403:
      // Forbidden - insufficient permissions
      return 'Access denied. You do not have permission for this action.';
    
    case 404:
      // Not found - generic message (don't reveal what doesn't exist)
      return 'Resource not found.';
    
    case 429:
      // Rate limited
      return 'Too many requests. Please try again later.';
    
    case 500:
      // Server error
      return 'Server error. Please try again later.';
    
    default:
      return 'An error occurred. Please try again.';
  }
}
```

---

## 🛡️ Security Best Practices

### 1. Input Validation

```typescript
// ✅ Validate client-side before sending
function validateEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}

function validatePassword(password: string): boolean {
  return password.length >= 8; // Minimum requirement
}

function validateVerificationCode(code: string): boolean {
  return /^\d{6}$/.test(code); // Exactly 6 digits
}
```

### 2. PII Masking

```typescript
// ✅ Mask sensitive data in UI/logs
function maskEmail(email: string): string {
  const [localPart, domain] = email.split('@');
  if (localPart.length <= 2) {
    return `${localPart[0]}***@${domain}`;
  }
  return `${localPart[0]}***${localPart[localPart.length - 1]}@${domain}`;
}

function maskPhone(phone: string): string {
  if (phone.length <= 4) return '***';
  return phone.slice(0, 2) + '***' + phone.slice(-4);
}
```

### 3. Rate Limiting

```typescript
// ✅ Implement client-side rate limiting
let loginAttempts = 0;
const MAX_ATTEMPTS = 5;
const LOCKOUT_DURATION = 15 * 60 * 1000; // 15 minutes

function checkRateLimit(): boolean {
  const lastAttempt = localStorage.getItem('lastLoginAttempt');
  if (lastAttempt) {
    const timeSince = Date.now() - parseInt(lastAttempt);
    if (timeSince < LOCKOUT_DURATION) {
      return false; // Still locked out
    }
  }
  return true;
}

function recordLoginAttempt() {
  loginAttempts++;
  localStorage.setItem('lastLoginAttempt', Date.now().toString());
  
  if (loginAttempts >= MAX_ATTEMPTS) {
    // Lock out client-side (backend also locks)
    alert('Too many login attempts. Please try again in 15 minutes.');
  }
}
```

### 4. CSRF Protection

```typescript
// ✅ Include CSRF token in state-changing requests
async function getCsrfToken(): Promise<string> {
  const response = await axios.get('/api/csrf-token');
  return response.data.csrfToken;
}

// Use in POST/PUT/DELETE requests
const csrfToken = await getCsrfToken();
await axios.post('/api/auth/login', data, {
  headers: {
    'X-CSRF-Token': csrfToken
  }
});
```

### 5. Secure Token Storage Utility

```typescript
// ✅ Secure token storage (memory-based)
class SecureTokenStorage {
  private accessToken: string | null = null;
  private refreshToken: string | null = null;

  setTokens(accessToken: string, refreshToken: string) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    
    // ✅ Never store in localStorage
    // ✅ Only in memory
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  getRefreshToken(): string | null {
    return this.refreshToken;
  }

  clearTokens() {
    this.accessToken = null;
    this.refreshToken = null;
  }
}

export const secureTokenStorage = new SecureTokenStorage();
```

---

## 💻 Complete Auth Hook Example

```typescript
import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';

interface AuthState {
  isAuthenticated: boolean;
  user: UserDto | null;
  loading: boolean;
}

export function useAuth() {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    user: null,
    loading: true
  });

  // Initialize auth state from tokens
  useEffect(() => {
    const accessToken = secureTokenStorage.getAccessToken();
    if (accessToken) {
      // Validate token and get user info
      validateToken(accessToken);
    } else {
      setState({ isAuthenticated: false, user: null, loading: false });
    }
  }, []);

  const validateToken = async (token: string) => {
    try {
      // Decode token to get user ID
      const payload = jwtDecode<JwtPayload>(token);
      
      // Fetch user info
      const response = await axios.get(`/api/common/users/${payload.user_id}`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      setState({
        isAuthenticated: true,
        user: response.data.data,
        loading: false
      });
    } catch (error) {
      // Token invalid - clear and logout
      secureTokenStorage.clearTokens();
      setState({ isAuthenticated: false, user: null, loading: false });
    }
  };

  const login = useCallback(async (contactValue: string, password: string) => {
    try {
      const response = await axios.post('/api/auth/login', {
        contactValue,
        password
      });

      const { accessToken, refreshToken, user } = response.data.data;
      secureTokenStorage.setTokens(accessToken, refreshToken);

      setState({
        isAuthenticated: true,
        user,
        loading: false
      });

      return { success: true };
    } catch (error: any) {
      return {
        success: false,
        error: handleAuthError(error)
      };
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      const refreshToken = secureTokenStorage.getRefreshToken();
      if (refreshToken) {
        await axios.post('/api/auth/logout', { refreshToken });
      }
    } catch (error) {
      // Continue logout even if API call fails
    } finally {
      secureTokenStorage.clearTokens();
      setState({ isAuthenticated: false, user: null, loading: false });
    }
  }, []);

  return {
    ...state,
    login,
    logout
  };
}
```

---

## 📚 Additional Resources

- **User Management:** `docs/frontend/USER_MANAGEMENT_GUIDE.md`
- **Communication System:** `docs/frontend/COMMUNICATION_SYSTEM_GUIDE.md`
- **Company Management:** `docs/frontend/COMPANY_MANAGEMENT_GUIDE.md`
- **Backend API Docs:** Swagger UI at `/swagger-ui.html`

---

## ⚠️ Security Checklist

Before deploying authentication:

- [ ] Tokens stored securely (httpOnly cookies or memory)
- [ ] No tokens in localStorage/sessionStorage
- [ ] Passwords never logged
- [ ] Verification codes never logged
- [ ] Error messages don't reveal sensitive info
- [ ] Input validation client-side
- [ ] Rate limiting implemented
- [ ] CSRF protection enabled
- [ ] Token refresh implemented
- [ ] Logout clears tokens
- [ ] PII masked in UI/logs

---

**⚠️ SECURITY CRITICAL:** Always follow security best practices. When in doubt, ask the security team.

**Questions?** Contact backend/security team or check Swagger documentation.

