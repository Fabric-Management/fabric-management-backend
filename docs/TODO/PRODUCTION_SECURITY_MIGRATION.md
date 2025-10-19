# 🔐 Production Security Migration Plan

> **Current:** Static Internal API Key (Development)  
> **Target:** Service-to-Service JWT + mTLS (Production)  
> **Priority:** HIGH (before production deployment)  
> **Estimated Effort:** 2-3 hours (JWT), 1-2 days (mTLS)

---

## 📊 Current State Analysis

### ✅ What's Working (Development)

**Static Internal API Key:**

```yaml
# docker-compose.yml
INTERNAL_API_KEY: ccbb9770451536b7bc9645a155ab9110eca9b4db7e4b8037b2b835dec61756cb
```

**Endpoints using Internal API Key:**

**Company-Service:**

- `POST /api/v1/companies/check-duplicate` (onboarding validation)

**Contact-Service:**

- `PUT /contacts/{id}/verify` (password setup orchestration)
- `POST /contacts/check-availability` (email uniqueness)
- `GET /contacts/check-domain` (domain uniqueness)
- `GET /contacts/find-by-value` (login flow)

**Benefits (Development):**

- ✅ Fast development
- ✅ Simple to understand
- ✅ No token rotation overhead
- ✅ Works in test environment

---

### ❌ Why It's NOT Production-Ready

| Issue                | Impact                                          | Risk Level  |
| -------------------- | ----------------------------------------------- | ----------- |
| **No Rotation**      | Key leaked → all internal endpoints compromised | 🔴 CRITICAL |
| **No Scope Control** | Single key = all permissions                    | 🔴 HIGH     |
| **No Audit Trail**   | Can't track which service called what           | 🟡 MEDIUM   |
| **Not Standard**     | Not OAuth2/OIDC compliant                       | 🟡 MEDIUM   |
| **Static Secret**    | Hard to rotate without downtime                 | 🔴 HIGH     |

**Manifesto Violation:**

- ❌ "GOOGLE/AMAZON LEVEL" → They use OAuth2 client credentials
- ❌ "PRODUCTION-READY" → Not enterprise-grade security
- ⚠️ "ZERO HARDCODED" → Key is in environment (better than code, but not ideal)

---

## 🚀 Migration Roadmap

### Phase 1: Development (NOW) ✅

**Status:** In Progress  
**Duration:** N/A (current state)  
**Approach:** Static Internal API Key

**Keep:**

- Static key for fast iteration
- Simple Feign client config
- No token management overhead

**Prepare:**

- Document all internal endpoints
- List service-to-service dependencies
- Design JWT scope model

---

### Phase 2: Staging/QA (NEXT)

**Status:** 🔴 NOT STARTED  
**Duration:** 2-3 hours  
**Target:** Before production deployment  
**Approach:** Service-to-Service JWT (OAuth2 Client Credentials)

#### 🎯 Implementation Plan

**Step 1: Create Auth Token Service (30 min)**

```java
// shared-security/oauth2/ServiceTokenProvider.java
@Service
public class ServiceTokenProvider {

    private final JwtTokenProvider jwtProvider;

    /**
     * Generate service-to-service JWT token
     *
     * @param serviceId Source service (user-service, company-service, etc.)
     * @param targetService Target service
     * @param scopes Permissions (service:company:read, service:contact:write)
     * @return JWT token (15 min expiry, auto-refresh)
     */
    public String generateServiceToken(String serviceId, String targetService, String... scopes) {
        Map<String, Object> claims = Map.of(
            "iss", "fabric-auth-service",
            "sub", serviceId,  // user-service, company-service, etc.
            "aud", targetService,  // company-service, contact-service, etc.
            "scope", String.join(" ", scopes),  // service:company:read service:company:write
            "type", "SERVICE"  // Distinguish from user JWT
        );

        return jwtProvider.generateToken(
            serviceId,
            "SYSTEM",  // No tenantId for service tokens
            claims,
            Duration.ofMinutes(15)  // Short-lived, auto-refresh
        );
    }
}
```

---

**Step 2: Create Feign OAuth2 Interceptor (45 min)**

```java
// shared-security/feign/OAuth2FeignRequestInterceptor.java
@Component
public class OAuth2FeignRequestInterceptor implements RequestInterceptor {

    private final ServiceTokenProvider tokenProvider;
    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    @Value("${spring.application.name}")
    private String serviceId;  // user-service, company-service, etc.

    @Override
    public void apply(RequestTemplate template) {
        String targetService = extractTargetService(template);  // From URL
        String[] scopes = determineScopes(template);  // Based on endpoint

        String token = getOrRefreshToken(serviceId, targetService, scopes);
        template.header("Authorization", "Bearer " + token);
    }

    private String getOrRefreshToken(String serviceId, String targetService, String[] scopes) {
        String cacheKey = serviceId + ":" + targetService;
        CachedToken cached = tokenCache.get(cacheKey);

        // If token exists and not expired (with 1-min buffer)
        if (cached != null && cached.expiresAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            return cached.token;
        }

        // Generate new token
        String newToken = tokenProvider.generateServiceToken(serviceId, targetService, scopes);
        tokenCache.put(cacheKey, new CachedToken(newToken, LocalDateTime.now().plusMinutes(15)));

        return newToken;
    }

    @Data
    @AllArgsConstructor
    private static class CachedToken {
        String token;
        LocalDateTime expiresAt;
    }
}
```

---

**Step 3: Update InternalAuthenticationFilter (30 min)**

```java
// shared-security/filter/InternalAuthenticationFilter.java
@Override
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {

    String authHeader = httpRequest.getHeader("Authorization");

    // Check if Bearer token (new approach)
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);

        // Validate service token
        if (jwtTokenProvider.validateToken(token)) {
            Claims claims = jwtTokenProvider.getClaims(token);

            // Check if SERVICE token (not user token)
            if ("SERVICE".equals(claims.get("type"))) {
                String serviceId = claims.getSubject();  // user-service, company-service
                String[] scopes = claims.get("scope", String.class).split(" ");

                // Create service security context
                SecurityContext serviceContext = SecurityContext.builder()
                    .userId(SecurityConstants.INTERNAL_SERVICE_PRINCIPAL)
                    .serviceId(serviceId)  // NEW: Track which service
                    .scopes(scopes)  // NEW: Track permissions
                    .build();

                // Set authentication
                // ... continue
            }
        }
    }

    // Fallback: Check static API key (backward compatibility during migration)
    if (endpointRegistry.isInternalEndpoint(path, method)) {
        String apiKey = httpRequest.getHeader("X-Internal-API-Key");
        // ... existing logic ...
    }
}
```

---

**Step 4: Configure Feign Clients (15 min)**

```java
// user-service FeignClient config
@Configuration
public class FeignConfig {

    @Bean
    public OAuth2FeignRequestInterceptor oauth2Interceptor(ServiceTokenProvider tokenProvider) {
        return new OAuth2FeignRequestInterceptor(tokenProvider);
    }
}

// Update @FeignClient annotations (no code change needed!)
@FeignClient(name = "company-service", configuration = FeignConfig.class)
public interface CompanyServiceClient {
    // Interceptor otomatik token ekleyecek!
}
```

---

**Step 5: Scope Model Design (30 min)**

```yaml
# Scope Matrix
service:company:read       → GET /companies/*
service:company:write      → POST/PUT/DELETE /companies/*
service:company:validate   → POST /companies/check-duplicate
service:contact:read       → GET /contacts/*
service:contact:write      → POST/PUT /contacts/*
service:contact:verify     → PUT /contacts/{id}/verify
service:user:read          → GET /users/*
service:user:write         → POST/PUT /users/*
```

**Service Permissions:**

```yaml
user-service:
  - service:company:validate # check-duplicate
  - service:company:write # create company (onboarding)
  - service:contact:write # create contact
  - service:contact:verify # verify contact
  - service:contact:read # find by value (login)

company-service:
  - service:contact:read # get company contacts
  - service:user:read # get company users

contact-service:
  - service:user:read # get contact owner info
```

---

### Phase 3: Production Scale (FUTURE)

**Status:** 🔴 NOT STARTED  
**Duration:** 1-2 days  
**Target:** After Kubernetes deployment  
**Approach:** JWT + mTLS (Zero-Trust)

#### 🎯 Implementation (When Scaling)

**Istio Service Mesh:**

```yaml
# Mutual TLS + JWT validation
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
spec:
  mtls:
    mode: STRICT # Force mTLS for all service-to-service
```

**Cert-Manager (Auto-rotation):**

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: user-service-cert
spec:
  secretName: user-service-tls
  duration: 2160h # 90 days
  renewBefore: 360h # Renew 15 days before expiry
  issuerRef:
    name: fabric-ca-issuer
```

---

## 📋 Migration Checklist

### Phase 1 → Phase 2 (Before Production)

- [ ] Implement `ServiceTokenProvider` in `shared-security`
- [ ] Create `OAuth2FeignRequestInterceptor`
- [ ] Update `InternalAuthenticationFilter` (dual-mode: JWT + static key)
- [ ] Configure all Feign clients with OAuth2 interceptor
- [ ] Define scope model (permission matrix)
- [ ] Test service-to-service JWT flow end-to-end
- [ ] Update documentation (API docs, deployment docs)
- [ ] Add monitoring (token generation, refresh, failures)
- [ ] Gradual rollout (JWT first, remove static key later)
- [ ] **REMOVE** static API key from environment (final step!)

### Phase 2 → Phase 3 (Kubernetes Migration)

- [ ] Deploy to Kubernetes cluster
- [ ] Install Istio service mesh
- [ ] Install cert-manager
- [ ] Configure mTLS policies
- [ ] Set up certificate rotation
- [ ] Monitor certificate expiry
- [ ] Test zero-trust network policies
- [ ] Update deployment docs

---

## 🎯 Decision Matrix: When to Migrate?

| Trigger                      | Action                  |
| ---------------------------- | ----------------------- |
| **Moving to staging**        | Implement JWT (Phase 2) |
| **Production deployment**    | JWT **MANDATORY**       |
| **Kubernetes deployment**    | Plan mTLS (Phase 3)     |
| **Scale beyond 10 services** | mTLS recommended        |
| **Compliance audit**         | JWT + mTLS required     |

---

## 📝 Reference Implementation (For Future)

**Google Cloud Run (Service-to-Service Auth):**

```yaml
# Uses OIDC tokens automatically
gcloud run services add-iam-policy-binding company-service \
--member="serviceAccount:user-service@project.iam.gserviceaccount.com" \
--role="roles/run.invoker"
```

**AWS ECS (Task IAM Roles):**

```yaml
TaskRoleArn: arn:aws:iam::123:role/UserServiceRole
Permissions:
  - Effect: Allow
    Action: ecs:InvokeTask
    Resource: arn:aws:ecs:*:*:task/company-service/*
```

**Netflix (Eureka + Zuul):**

```java
// Service mesh with automatic mTLS
@EnableEurekaClient
@EnableZuulProxy
public class GatewayApplication {
    // Auto-service discovery + mTLS
}
```

---

## ⚠️ DO NOT FORGET!

**Before Production:**

1. ✅ Remove static API key from all environments
2. ✅ Implement JWT service-to-service auth
3. ✅ Test token rotation
4. ✅ Monitor token failures
5. ✅ Audit log all service-to-service calls
6. ✅ Document migration in deployment guide

**Quote from the team:**

> "Static key is a **development convenience**, NOT a production strategy!"  
> — AI Assistant Learning, Oct 19, 2025

---

## 🔗 Related Documents

- [AI_ASSISTANT_CRITICAL_LESSONS.md](../development/AI_ASSISTANT_CRITICAL_LESSONS.md) - Framework best practices
- [principles.md](../development/principles.md) - Zero hardcoded values
- [ROLES_QUICK_REFERENCE.md](../architecture/ROLES_QUICK_REFERENCE.md) - JWT structure

---

**Created:** October 19, 2025  
**Status:** 🔴 TODO (Phase 2 pending)  
**Owner:** Backend Team  
**Review:** Before staging deployment
