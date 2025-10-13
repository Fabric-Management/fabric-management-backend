# Performance Tuning Guide

**Version:** 1.0  
**Date:** October 13, 2025  
**Purpose:** How to optimize timeouts and circuit breakers based on production metrics

---

## 📊 Overview

All timeout and circuit breaker configurations support **environment variable overrides**, allowing you to tune performance based on real-world metrics without code changes.

---

## 🎯 Timeout Configuration Strategy

### The Formula

```
Recommended Timeout = P95 Response Time × 1.5 to 2.0

Examples:
- P95 = 7s  → Timeout 10.5s to 14s  (%50–100 buffer)
- P95 = 5s  → Timeout 7.5s to 10s
- P95 = 3s  → Timeout 4.5s to 6s
```

### Why P95 (not average)?

- **Average (P50):** Too aggressive, will timeout 50% of requests!
- **P95:** Balanced - allows 95% of requests to complete
- **P99:** Too conservative, wastes resources on outliers

### Why +50% to +100% Buffer?

- **Network variance:** Latency spikes, packet loss
- **Load variance:** Peak hours vs low traffic
- **Dependency variance:** Database slow queries, cache misses
- **Safety margin:** Prevents false timeouts

---

## 🛠️ How to Measure & Tune

### Step 1: Measure Current Performance

**Use Prometheus/Grafana:**

```promql
# P95 response time (95th percentile)
histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket{uri="/api/v1/public/onboarding/register"}[5m])
)

# P99 response time (99th percentile)
histogram_quantile(0.99,
  rate(http_server_requests_seconds_bucket{uri="/api/v1/public/onboarding/register"}[5m])
)
```

**Or use logs (simple approach):**

```bash
# Extract response times from logs
docker logs user-service 2>&1 | grep "response_time" | awk '{print $5}' | sort -n

# Get P95 (95th value out of 100)
# Example results:
# 3200ms, 3500ms, 4000ms, ..., 7000ms, 7500ms (P95), 8000ms, 9000ms, 12000ms (P99)
```

### Step 2: Calculate Optimal Timeout

**Example: Tenant Onboarding**

```
Measured:
- Average: 4s
- P95: 7s
- P99: 12s

Calculation:
- Conservative (P95 × 2.0): 7s × 2.0 = 14s
- Balanced (P95 × 1.5): 7s × 1.5 = 10.5s → Round to 11s
- Aggressive (P95 × 1.2): 7s × 1.2 = 8.4s (too tight!)

Decision: Use 15s (P95 × 2.0 + safety margin)
```

### Step 3: Apply Configuration

**Development (.env or docker-compose.yml):**

```yaml
environment:
  USER_SERVICE_TIMEOUT: 15s # Onboarding timeout
  COMPANY_SERVICE_TIMEOUT: 10s # Company operations
  CONTACT_SERVICE_TIMEOUT: 7s # Contact operations
```

**Production (Kubernetes ConfigMap):**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: api-gateway-config
data:
  USER_SERVICE_TIMEOUT: "12s" # Measured P95=8s → 12s (%50 buffer)
  COMPANY_SERVICE_TIMEOUT: "9s" # Measured P95=6s → 9s
  CONTACT_SERVICE_TIMEOUT: "5s" # Measured P95=3s → 5s
```

### Step 4: Monitor & Iterate

**Watch these metrics:**

```
1. Timeout Rate: Should be <1% of requests
   - Too high? Increase timeout
   - Zero timeouts? Decrease timeout (optimize resources)

2. Circuit Breaker Open Rate: Should be <5%
   - Too high? Adjust failure-rate-threshold or timeout
   - Never opens? System is very stable (good!)

3. P95 vs Timeout Gap: Should maintain 50–100% buffer
   - P95 increasing? Increase timeout
   - P95 decreasing? Can decrease timeout
```

---

## 📋 Environment Variables Reference

### API Gateway - Timeout Configuration

| Variable                  | Default | Description             | Tuning Guide                     |
| ------------------------- | ------- | ----------------------- | -------------------------------- |
| `USER_SERVICE_TIMEOUT`    | 15s     | User Service operations | P95 × 1.5–2.0 (onboarding heavy) |
| `COMPANY_SERVICE_TIMEOUT` | 10s     | Company operations      | P95 × 1.5–2.0                    |
| `CONTACT_SERVICE_TIMEOUT` | 7s      | Contact operations      | P95 × 1.5–2.0                    |
| `GATEWAY_DEFAULT_TIMEOUT` | 15s     | Fallback timeout        | Max of all services              |

### API Gateway - Rate Limiting

| Variable                            | Default | Description             | Tuning Guide           |
| ----------------------------------- | ------- | ----------------------- | ---------------------- |
| `GATEWAY_RATE_LOGIN_REPLENISH`      | 5       | Login requests/sec      | Anti-brute-force       |
| `GATEWAY_RATE_LOGIN_BURST`          | 10      | Login burst capacity    | 2× replenish rate      |
| `GATEWAY_RATE_ONBOARDING_REPLENISH` | 5       | Onboarding requests/sec | Anti-spam              |
| `GATEWAY_RATE_ONBOARDING_BURST`     | 10      | Onboarding burst        | 2× replenish rate      |
| `GATEWAY_RATE_PROTECTED_REPLENISH`  | 50      | Protected endpoints/sec | Based on expected load |
| `GATEWAY_RATE_PROTECTED_BURST`      | 100     | Protected burst         | 2× replenish rate      |

### API Gateway - Circuit Breaker

| Variable                         | Default | Description                       | Tuning Guide   |
| -------------------------------- | ------- | --------------------------------- | -------------- |
| `GATEWAY_CB_FAILURE_THRESHOLD`   | 50      | Failure % to open circuit         | 40–60% typical |
| `GATEWAY_CB_SLOW_CALL_THRESHOLD` | 50      | Slow call % to open               | 40–60% typical |
| `GATEWAY_CB_SLOW_CALL_DURATION`  | 8s      | What's considered "slow"          | P95 × 1.2      |
| `GATEWAY_CB_WAIT_DURATION`       | 30s     | How long circuit stays open       | 15s–60s        |
| `GATEWAY_CB_SLIDING_WINDOW`      | 100     | Sample size for decisions         | 50–200         |
| `GATEWAY_CB_MIN_CALLS`           | 10      | Min calls before circuit can open | 5–20           |

### API Gateway - Retry Configuration

| Variable                          | Default | Description                   | Tuning Guide                    |
| --------------------------------- | ------- | ----------------------------- | ------------------------------- |
| `GATEWAY_RETRY_PUBLIC_INITIAL`    | 100ms   | Public routes initial backoff | Higher for public (anti-abuse)  |
| `GATEWAY_RETRY_PROTECTED_INITIAL` | 50ms    | Protected routes initial      | Lower for authenticated users   |
| `GATEWAY_RETRY_MAX_BACKOFF`       | 500ms   | Maximum backoff duration      | Keep under 1s for UX            |
| `GATEWAY_RETRY_MULTIPLIER`        | 2.0     | Exponential multiplier        | 2.0 standard (50ms→100ms→200ms) |
| `GATEWAY_RETRY_MAX_ATTEMPTS`      | 3       | Maximum retry attempts        | 2–5 typical                     |

---

## 🚀 Example: Production Tuning

### Scenario: Black Friday (High Load)

**Measured Metrics:**

```
Tenant Onboarding:
- Average: 6s
- P95: 10s (slower due to load)
- P99: 18s (some very slow)
- Timeout events: 2% (too many!)
```

**Action: Increase Timeouts**

```yaml
environment:
  # Increase from 15s to 20s (P95=10s × 2.0)
  USER_SERVICE_TIMEOUT: 20s

  # Also increase circuit breaker threshold
  GATEWAY_CB_FAILURE_THRESHOLD: 60 # More lenient during high load
  GATEWAY_CB_SLOW_CALL_DURATION: 12s # P95=10s × 1.2
```

**Result:**

```
✅ Timeout rate: 2% → 0.3%
✅ Circuit breaker: More stable
✅ User experience: Better (less failures)
```

---

### Scenario: Normal Load Optimization

**Measured Metrics:**

```
Tenant Onboarding:
- Average: 3s
- P95: 5s (very fast!)
- P99: 8s
- Timeout events: 0% (never times out)
```

**Action: Optimize Resources**

```yaml
environment:
  # Decrease from 15s to 10s (P95=5s × 2.0)
  # Still safe but more efficient
  USER_SERVICE_TIMEOUT: 10s

  # Tighter circuit breaker (fail fast)
  GATEWAY_CB_SLOW_CALL_DURATION: 6s # P95=5s × 1.2
```

**Result:**

```
✅ Faster failure detection
✅ Better resource utilization
✅ Same reliability (0% timeout)
```

---

## 🎯 Best Practices

### 1. **Start Conservative** 🛡️

```
Initial deployment:
- Use P95 × 2.0 (generous buffer)
- Monitor for 1 week
- Adjust based on data
```

### 2. **Monitor Continuously** 📊

```
Weekly review:
- Check P95/P99 trends
- Watch timeout rates
- Adjust if needed
```

### 3. **Service-Specific Tuning** 🎯

```
Different services need different timeouts:
- User Service: 15s (complex onboarding)
- Company Service: 10s (database-heavy)
- Contact Service: 7s (simple CRUD)
```

### 4. **Circuit Breaker Anti-Patterns** ⚠️

**❌ Too Aggressive:**

```yaml
failure-rate-threshold: 10 # Opens circuit too easily!
slow-call-duration: 2s # Everything is "slow"!
```

**✅ Balanced:**

```yaml
failure-rate-threshold: 50 # Opens when 50% fail
slow-call-duration: 8s # Reasonable threshold
```

---

## 📈 Monitoring Dashboard

### Key Metrics to Track

**Response Time:**

- `http_server_requests_seconds{quantile="0.95"}` - P95
- `http_server_requests_seconds{quantile="0.99"}` - P99
- `http_server_requests_seconds_sum / http_server_requests_seconds_count` - Average

**Circuit Breaker:**

- `resilience4j_circuitbreaker_state` - Current state (0=closed, 1=open, 2=half_open)
- `resilience4j_circuitbreaker_failure_rate` - Current failure rate
- `resilience4j_circuitbreaker_slow_call_rate` - Current slow call rate

**Timeouts:**

- `resilience4j_timelimiter_calls_total{kind="timeout"}` - Total timeouts
- Timeout rate = timeouts / total_requests

---

## 🔧 Quick Tuning Commands

### Get Current Configuration

```bash
# Check current timeout settings
docker exec api-gateway env | grep TIMEOUT

# Check current circuit breaker settings
docker exec api-gateway env | grep GATEWAY_CB
```

### Update Configuration (Docker Compose)

```bash
# Edit docker-compose.yml
vim docker-compose.yml

# Add/update in api-gateway service:
environment:
  USER_SERVICE_TIMEOUT: 20s
  GATEWAY_CB_FAILURE_THRESHOLD: 60

# Restart
docker-compose up -d api-gateway
```

### Update Configuration (Kubernetes)

```bash
# Update ConfigMap
kubectl edit configmap api-gateway-config

# Rollout restart
kubectl rollout restart deployment/api-gateway
```

---

## ✅ Configuration Checklist

Before going to production:

- [ ] Measure P95/P99 for all critical endpoints
- [ ] Set timeouts to P95 × 1.5 to 2.0
- [ ] Configure all environment variables
- [ ] Test circuit breaker behavior (simulate failures)
- [ ] Set up monitoring dashboard
- [ ] Document baseline metrics
- [ ] Plan weekly review process

---

## 📚 Related Documentation

- `docs/deployment/ENVIRONMENT_VARIABLES.md` - All environment variables
- `docs/reports/.../API_GATEWAY_REFACTOR_OCT_13_2025.md` - Gateway architecture
- `docs/troubleshooting/TENANT_ONBOARDING_503_TIMEOUT_ISSUE.md` - Timeout debugging

---

**Author:** Fabric Management Team  
**Last Updated:** October 13, 2025  
**Production Ready:** ✅ Yes
