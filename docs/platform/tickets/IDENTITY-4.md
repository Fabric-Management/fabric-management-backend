# IDENTITY-4 (BE+FE) — Phase 4a: organization switcher

> Epic: **Platform Identity & Memberships** (`../../architecture/ADR-0002-...`). Follows IDENTITY-1/2/3
> (identity+membership tables, login-on-identity, refresh — all done & validated). **Implementer:** Codex.
> **Reviewer:** Claude.
> Goal: let a person whose identity has **multiple memberships** see their organizations and **switch between
> them without re-authenticating** — the core of "full multi-tenant". Scope is the SWITCH experience only.
> Out of scope (P4b, later): login-time org-picker (single/default login already works), the "invite an existing
> email into another org" creation path (touches the provisioning collision rule), a standalone "My
> Organizations" page.

## Model recap (already built)
`login_identity` (global, no RLS) → many `membership` (login_identity_id, tenant_id, user_id, status, is_default;
tables are RLS-exempt). A tenant-scoped `User` per membership. Login/refresh issue a token for one membership's
tenant/user. P3 added an `identity_id` JWT claim.

## Backend

### Resolve "my identity" from the current user (no filter/context change)
The `membership` table is RLS-free, so it can be queried in any context. Derive the caller's identity from the
authenticated `userId` (the controllers already have `getCurrentUserId()`):
`membershipRepository.findByUserId(currentUserId)` → `getLoginIdentityId()`. (Do NOT rely on adding identity_id
to AuthenticatedUserContext for this ticket.)

### `GET /api/v1/auth/memberships`
Authenticated. Returns the current identity's memberships for an org switcher:
- identity = findByUserId(currentUserId).loginIdentityId; `memberships = membershipRepository.findByLoginIdentityIdAndStatus(identity, ACTIVE)`.
- Enrich tenant display via `TenantQueryPort.findAllByIds(tenantIds)` (BYPASSRLS — memberships span tenants).
- Return `List<OrganizationMembershipDto>` = { tenantId, tenantName (uid/name), userId, isCurrent (tenantId ==
  current context tenantId), isDefault }. Sort: current first, then name.
- If the caller has no membership row, return a single-item list derived from the current token (graceful).

### `POST /api/v1/auth/switch-org/{tenantId}`
Authenticated. Re-issues tokens for another of the caller's memberships. New `SwitchOrganizationService` (or add
to an existing auth service):
1. identity = findByUserId(currentUserId).loginIdentityId.
2. `target = membershipRepository.findByLoginIdentityIdAndTenantId(identity, tenantId)` → if absent or not
   ACTIVE → 403 `AUTH_MEMBERSHIP_NOT_FOUND` (a caller may only switch to their OWN memberships — this is the
   authorization check).
3. `TenantContext.setCurrentTenantId(tenantId)` + `tenantSessionBinder.bindToCurrentSession(tenantId)`.
4. Load the target `User` (`userRepository.findByTenantIdAndId(tenantId, target.getUserId())`); check active.
5. Issue access + refresh tokens for that user and persist the refresh token (same as login/refresh — reuse the
   existing token-issue + refresh-persist path; do not duplicate rotation logic gratuitously). Return
   `LoginResponse`.
- Controller sets the tokens as HttpOnly cookies via `authCookieSupport.addAuthCookies(...)` (exactly like
  `/login`), and strips them from the body.

Security: switching is constrained to memberships of the caller's own identity (step 2). No password needed
(already authenticated).

## Frontend
- Service + hooks (features/auth or features/trial as fits): `getMyOrganizations()` (GET /auth/memberships) and
  `switchOrganization(tenantId)` (POST /auth/switch-org/{tenantId}). After a successful switch, refetch the
  current user (cookies are already updated) and let the app re-render in the new org.
- **Org switcher in the sidebar account menu** (`UserProfileMenu`), shown only when the user has **more than one**
  membership: show the current org name, and a list of the other orgs; clicking one switches. Keep it minimal and
  consistent with the existing menu sections (same visual language as the playground section we added). A full
  standalone "My Organizations" page is P4b — the menu list is enough here.
- Regenerate API types (`pnpm sync:spec && pnpm generate:api`) after the BE endpoints exist; use `.nullish()` for
  any optional fields in hand-written types/schemas.

## Acceptance
- An identity with 2+ active memberships sees them in the account-menu switcher (current org marked); a
  single-membership user sees no switcher (or just their current org, no switch action).
- Clicking another org calls switch-org, receives new cookies, and the app re-renders as that org's user WITHOUT
  a re-login. Switching back works too.
- Switching to a tenant the identity is NOT a member of → 403 (authorization enforced server-side).
- BE unit tests: list memberships for an identity; switch success (membership belongs to identity → tokens
  issued); switch rejected for a non-member tenant (403); enrich tenant names. FE: switcher renders with >1 org,
  hidden with 1; switch calls the endpoint + refetches. Existing tests stay green.

## Testing note + seed (Fatih runs when validating)
Everyone currently has exactly ONE membership (backfill), so to see the switcher you need an identity with two.
Simplest dev seed (repoint one existing backfilled membership onto your identity) — Claude will hand the exact
SQL separately; run it against the local DB, then log in and try the switcher. (The real production path to gain
a second membership is P4b — invite an existing email into another org.)
Integration tests run as a Postgres superuser (RLS bypassed) → cover logic with unit tests; validate the switch
on the real DB.

## Codex runs nothing
Codex writes BE + FE + tests, hands Fatih `./mvnw -q fmt:format` + backend build/test commands, the FE
`pnpm` checks, and a conventional-commit message in `COMMIT_MSG_IDENTITY4.txt` (subject lowercase). Fatih runs
everything, pastes output back; escalate to Claude if unsolved. Read canon/epic only if not already read.

---
### Türkçe özet (Fatih için)
P4a = **organizasyonlar arası geçiş.** Backend: `GET /auth/memberships` (kimliğimin org'ları) + `POST
/auth/switch-org/{tenantId}` (kimliğime ait başka bir membership için yeniden auth'suz token ver, cookie'leri
login gibi set et). Kimlik, mevcut `userId`'den türetilir (membership tablosu RLS'siz). Güvenlik: sadece KENDİ
membership'lerine geçebilirsin (değilse 403). Hedef org'a geçerken TenantContext'i hedefe alıp bind ederek hedef
User'ı yükleyip token üretir. FE: hesap menüsünde **org switcher** (birden çok org varsa; mevcut + diğerleri,
tıkla-geç-refetch). Test için senin kimliğine 2. bir membership'i SQL ile bağlayacağız (ayrı vereceğim). P4b
(başka org'a davet, login-picker, ayrı "Kuruluşlarım" ekranı) sonraki oturuma.
