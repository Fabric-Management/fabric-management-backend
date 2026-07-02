# IDENTITY-3 (BE) — Phase 3: fix refresh + logout-by-refresh (RLS pre-auth)

> Epic: **Platform Identity & Memberships** (`../../architecture/ADR-0002-...`). Follows IDENTITY-1 (tables +
> backfill, 6cc3add7) and IDENTITY-2 (login on identity, 2bc55afb). **Implementer:** Codex. **Reviewer:** Claude.
> **Side:** BE only.
> Goal: make **token refresh** work again (RLS-blinded → 401 today) using the same bypass-resolve-then-bind
> pattern as setup-password. This also fixes the FE "Return to my account" button (it calls refresh) and the
> ~15-min silent logout. MFA already moved to the identity in IDENTITY-2 — no MFA work here.

## Why (code-confirmed root cause)
`RefreshTokenService.refreshAccessToken` and `LogoutService.logoutByRefreshToken` are **anonymous / pre-auth**
(no tenant context), but they look up the refresh token via the RLS-bound JPA repo:
`refreshTokenRepository.findByToken(refreshToken)` on `common_auth.common_refresh_token` (RLS + FORCE). Under
the SYSTEM binding the row is invisible → "Invalid refresh token" (401). `refreshAccessToken` additionally calls
`TenantContext.requireTenantId()`, which has no tenant pre-auth. Net effect: refresh always 401s → the app
treats it as session-expired and redirects to login; and `useReturnToOwner` (FE, calls refresh) dumps the user
to login instead of returning them to the owner session.

## Pattern to reuse (same as setup-password, IDENTITY-2)
Resolve the token's tenant via the BYPASSRLS `TenantQueryPort`, then `TenantContext.setCurrentTenantId(tenantId)`
+ `tenantSessionBinder.bindToCurrentSession(tenantId)`, then run the existing RLS-bound flow (which now sees the
row under the correct tenant). Keep the privileged executor behind the whitelisted adapter (ArchUnit Rule 14.1).

## Changes

### 1. Port: cross-tenant refresh-token → tenant resolution
- `TenantQueryPort`: add `Optional<UUID> findTenantIdByRefreshToken(String token);` (javadoc: pre-auth refresh
  is cross-tenant; resolved via BYPASSRLS, mirrors `findTenantIdByRegistrationToken`).
- `TenantQueryAdapter`: implement via `SystemTransactionExecutor`:
  `SELECT tenant_id FROM common_auth.common_refresh_token WHERE token = ?` → `Optional.ofNullable(uuid)`.
  (Add a SQL constant next to the registration-token one.)

### 2. `RefreshTokenService.refreshAccessToken(refreshToken)`
- At the very start (before any RLS-bound read): `UUID tenantId = tenantQueryPort.findTenantIdByRefreshToken(refreshToken).orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));`
- `TenantContext.setCurrentTenantId(tenantId)` + `tenantSessionBinder.bindToCurrentSession(tenantId)`.
- Then keep the existing flow: `refreshTokenRepository.findByToken(...)` (now visible), validate (expired /
  `isRevoked`), load the user (`findByTenantIdAndId(tenantId, userId)`), rotate (revoke old + issue new
  refresh), issue new access token. **Remove the `TenantContext.requireTenantId()` reliance** — use the resolved
  `tenantId` (context is already set).
- Inject `TenantQueryPort` + `TenantSessionBinder`.

### 3. `LogoutService.logoutByRefreshToken(refreshToken)`
- Same bypass-resolve → `setCurrentTenantId` + `bindToCurrentSession` before `findByToken`, so anonymous
  logout-by-cookie can find + revoke the token. (`logout(refreshToken, userId)` — the authenticated path using
  `requireTenantId()` — is unchanged.)

### 4. JWT `identity_id` claim (additive, defensive)
- In `JwtService.buildCommonClaims(user)`, add `identity_id` resolved from the user's membership:
  `membershipRepository.findByUserId(user.getId()).map(Membership::getLoginIdentityId)` → put `identity_id`
  when present. If absent (legacy/edge), **skip the claim** (do not fail token generation). This sets up P4
  switch-org; do not otherwise change token claims.

## Acceptance
- A valid refresh token (anonymous call, no tenant context) refreshes successfully: tenant resolved via bypass,
  token rotated, new access + refresh returned. No more 401 on a valid token.
- Session survives past the access-token lifetime (silent refresh works); FE "Return to my account" returns to
  the owner and stays in the app (no login redirect).
- Invalid / expired / revoked refresh token → the same clear error as before (no RLS false-negative).
- `logoutByRefreshToken` finds + revokes the token from an anonymous (cookie-only) call.
- JWT carries `identity_id` for users that have a membership; token generation still works when they don't.
- New unit tests: refresh happy path (bypass-resolve + rotate), invalid/expired/revoked, logout-by-refresh
  resolve, identity_id present/absent. Existing auth tests stay green.

## Testing note
Integration tests run as a Postgres **superuser** (Testcontainers) → RLS bypassed, so they won't prove the
RLS-free win. Cover logic with unit tests; Fatih validates on the real (non-superuser) DB: log in, wait for the
access token to expire (or force a refresh), confirm the session stays alive; in playground, use "View as…" then
"Return to my account" and confirm it returns to the owner without a login redirect.

## Out of scope (later)
- P4: org-picker on multi-membership login + switch-organization UX (uses the new `identity_id`).
- Removing `AuthUser` (still dual-written); moving refresh tokens onto the identity layer (kept per-tenant here).
- System-worker RLS gap (email outbox + schedulers) — separate track.

## Codex runs nothing
Codex writes code + tests, hands Fatih `./mvnw -q fmt:format` + build/test commands + a conventional-commit
message in `COMMIT_MSG_IDENTITY3.txt` (subject lowercase). Fatih runs everything, pastes output back; escalate to
Claude if unsolved. Read canon/epic only if not already read this session.

---
### Türkçe özet (Fatih için)
P3 = **refresh'i düzeltmek.** Bugün refresh anonim ama RLS'li tablodan token'ı arıyor → göremiyor → 401 → app
"session bitti" deyip login'e atıyor; "Return to my account" da (refresh çağırdığı için) login'e düşüyor.
Çözüm setup-password'un aynısı: refresh token'ın tenant'ını **BYPASSRLS** ile çöz → `TenantContext.set` +
`TenantSessionBinder.bind` → sonra normal akış (bul/doğrula/rotate) RLS altında çalışır. `TenantQueryPort`'a
`findTenantIdByRefreshToken` ekliyoruz; `RefreshTokenService` ve `LogoutService.logoutByRefreshToken` bunu
kullanıyor. Ek olarak JWT'ye küçük, güvenli bir `identity_id` claim'i (P4 switch-org için). MFA zaten P2'de
identity'ye geçmişti, ona dokunmuyoruz. Bu tek fix hem **session yenilemeyi** hem **"Return to my account"u**
düzeltir.
