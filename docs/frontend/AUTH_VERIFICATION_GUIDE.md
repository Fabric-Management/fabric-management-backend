# Authentication & Verification Frontend Integration Guide

## 1. Overview

This guide explains how the web client should integrate with the updated authentication and verification framework. The backend now enforces secure, single-use verification codes, channel-aware delivery, tenant isolation, and rate limiting. Frontend behaviour must reflect these guarantees while keeping the UX simple and reassuring.

Key principles:
- Users authenticate solely with registered contact information (email or phone).
- Verification channel is inferred automatically: phone contacts with `isWhatsApp=true` use WhatsApp, otherwise SMS; emails use email channel.
- No UI exposure of whether a contact exists; error messages are generic.
- Verification codes are short-lived (≈10 minutes), single-use, and throttled.
- Password creation/reset is a continuation of the same verification flow (no separate flags like `isForAuthentication`).

## 2. Flow Diagram

```mermaid
directive TD

flowchart TD
    A[Enter Email/Phone] --> B{Registered Contact?}
    B -- No --> H[Generic success message
("If this contact is registered...")]
    B -- Yes & Verified --> C[Prompt for Password]
    B -- Yes & Unverified --> D[Request Verification Code]
    D --> E[Code Delivery
(WhatsApp > SMS > Email)]
    E --> F[Enter Verification Code]
    F -->|Valid| G[Set/Create Password]
    F -->|Invalid/Expired| D
    G --> I[Login Success → Redirect]

    subgraph Password Reset
        P1[Initiate Reset (Email/Phone)] --> P2[Select Masked Contact]
        P2 --> P3[Code Delivery]
        P3 --> P4[Enter Code]
        P4 -->|Valid| P5[Set New Password]
        P4 -->|Invalid| P3
    end
```

## 3. API Endpoints

All requests are JSON with `Content-Type: application/json`. Responses follow the standard `ApiResponse<T>` envelope.

### 3.1 POST `/auth/request-code`
Request a verification code for registration, login re-verification, or password reset.

```json
POST /auth/request-code
{
  "contactValue": "user@example.com",  // email or +E.164 phone number
  "purpose": "REGISTRATION"            // REGISTRATION | PASSWORD_RESET | LOGIN_VERIFICATION
}
```

Successful response (always generic):
```json
{
  "success": true,
  "message": "If this contact is registered, a verification code has been sent."
}
```

Possible error responses the UI must handle:
- `HTTP 429` + message (rate limit exceeded) → show “Too many attempts. Please try again later.”
- `HTTP 400` generic message → show server text without exposing whether contact exists.

### 3.2 POST `/auth/verify-code`
Validate the verification code. Backend ensures single-use and attempt tracking.

```json
POST /auth/verify-code
{
  "contactValue": "user@example.com",
  "code": "123456",
  "purpose": "REGISTRATION"
}
```

Response (success):
```json
{
  "success": true,
  "data": {
    "verificationToken": "1d648a1e-...",   // short-lived token for next step
    "expiresIn": 600                         // seconds remaining for password setup
  }
}
```

Failure scenarios:
- `code expired` → prompt to resend (UI resets countdown).
- `too many attempts` → disable code entry, require fresh code request.
- `invalid` → display generic “Code invalid or expired.”

### 3.3 POST `/auth/set-password`
Called after successful verification to set or reset password.

```json
POST /auth/set-password
{
  "verificationToken": "1d648a1e-...",
  "newPassword": "S3cure!Pass",
  "confirmPassword": "S3cure!Pass"
}
```

Response:
```json
{
  "success": true,
  "data": {
    "accessToken": "JWT...",
    "refreshToken": "JWT...",
    "expiresIn": 900
  }
}
```

### 3.4 POST `/auth/login`
Standard login using verified contact + password.

```json
POST /auth/login
{
  "contactValue": "user@example.com",
  "password": "S3cure!Pass"
}
```

Handle:
- `HTTP 401` invalid credentials (show generic message).
- `HTTP 423` locked (too many attempts) → inform user to retry later.
- Successful response returns access/refresh tokens as above.

### 3.5 GET `/user/contacts`
Used when multiple verified contacts exist (password reset, resend flow).

```json
GET /user/contacts
Authorization: Bearer <token>
```

Response:
```json
{
  "success": true,
  "data": [
    {
      "authUserId": "ef21...",
      "masked": "a•••@example.com",
      "type": "EMAIL",
      "isPrimary": true,
      "isVerified": true
    },
    {
      "authUserId": "ab91...",
      "masked": "+44•••6789",
      "type": "PHONE",
      "isPrimary": false,
      "isVerified": true,
      "channel": "WHATSAPP"      // derived from backend flag
    }
  ]
}
```

### 3.6 POST `/user/contacts/select`
Select a contact for subsequent verification delivery.

```json
POST /user/contacts/select
Authorization: Bearer <token>
{
  "authUserId": "ef21..."
}
```

Response mirrors `/auth/request-code` generic success messaging.

### 3.7 GET `/auth/status`
Check current state to drive conditional UI (e.g., show password modal vs. login form).

```json
GET /auth/status
Authorization: Bearer <token>
```

Response example:
```json
{
  "success": true,
  "data": {
    "isAuthenticated": false,
    "needsVerification": true,
    "pendingPurpose": "PASSWORD_RESET",
    "remainingAttempts": 2,
    "resendAt": "2025-11-08T12:34:56Z"
  }
}
```

## 4. UI / UX Requirements

### 4.1 Contact Input
- Single input field accepting either email or E.164 phone.
- Auto-trim whitespace, lowercase emails, preserve phone `+` prefix.
- Show inline hint: “Enter your email or phone number.”
- On submit, always display generic confirmation message.

### 4.2 Verification Step
- Modal or stepper layout with sections:
  1. Contact entered (read-only, masked if re-displayed).
  2. Code entry with masked channel label (e.g., “Code sent to a•••@example.com”).
  3. Resend button disabled until timer completes (default 60s).
  4. Show fallback badge if backend indicates SMS fallback (display text “Sent via SMS instead of WhatsApp”).
- Countdown timer resets after each resend.
- Provide “Change contact” link to trigger `/user/contacts` retrieval when available.

### 4.3 Password Creation/Reset
- Display password rules inline.
- If verification token expires before completion, show message and redirect to contact entry.
- On success, show confirmation toast and redirect to dashboard/login depending on flow.

### 4.4 Error Handling
- Use generic messages to avoid leaking existence of contacts.
- For rate limiting or lockouts:
  - Rate limit: “Too many attempts. Please try again later.”
  - Lockout: “Your account is temporarily locked due to multiple failed attempts.”
- For expired codes: “Code expired. Request a new one.” Provide direct resend action.

### 4.5 Masked Contact Display
- Use backend-provided masked values; do not attempt to mask client-side (ensures consistency).
- Indicate verification channel with icon/text (Email, WhatsApp, SMS).
- Highlight verified contacts (checkmark) vs pending (clock icon, disabled selection).

### 4.6 Resend UX
- Show disabled resend button with timer, e.g., “Resend in 00:32”.
- After timer completes, button label “Resend code”.
- If resend fails (rate limit), keep countdown, display message under input.

### 4.7 Accessibility & Responsive Considerations
- Ensure modals and forms are fully keyboard navigable.
- Provide ARIA live regions for error/success messages.
- Maintain responsive layout for mobile by using vertical stacked steps.
- Use sufficient colour contrast for status badges (verified/unverified, fallback channel).

## 5. Token & Session Handling
- Store short-lived `verificationToken` in memory (e.g., React state or secure store) – do not persist in localStorage.
- Access/refresh tokens should follow existing secure storage policy (HttpOnly cookies recommended if already in place).
- Clear verification state when navigating away or after successful password setup.

## 6. Suggested UX Copy (Generic)
- Request Code: “If this contact is registered, a verification code has been sent.”
- Code Invalid: “Verification code is invalid or expired.”
- Code Expired: “Verification code expired. Request a new code.”
- Rate Limit: “Too many attempts. Please try again later.”
- Lockout: “Your account is temporarily locked. Please retry later or contact your administrator.”

## 7. Developer Checklist
- [ ] Use `/auth/request-code` for initial step; never expose contact existence.
- [ ] Poll `/auth/status` when deciding whether to show password creation vs. login form.
- [ ] Implement resend timer + fallback messaging per backend response.
- [ ] Use `/user/contacts` and `/user/contacts/select` to manage alternate contacts.
- [ ] Masked contact display must match backend values.
- [ ] Handle `HTTP 429` gracefully across all calls.
- [ ] Log client-side events for analytics (e.g., code resent, fallback used) without storing actual contact values.

## 8. Coordination Notes
- If frontend needs additional metadata (e.g., exact resend wait time), request contract updates from backend.
- Align copy with product/design; ensure localization uses placeholders for contact type.
- If designing new UI patterns (e.g., progressive disclosure), validate that backend state machine (`/auth/status`) supports it.

## 9. Future Enhancements (Optional)
- Consider device fingerprint or WebAuthn for high-security tenants.
- Provide in-app notification banner when tenant WhatsApp integration fails and platform fallback is used.
- Offer offline code entry support (QR or TOTP) once additional factors are introduced.

---
For questions or adjustments, coordinate with the backend authentication team to keep contracts aligned.
