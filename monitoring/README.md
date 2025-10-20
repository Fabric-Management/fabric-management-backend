# Monitoring Stack - Grafana + Prometheus + Alertmanager

## Overview

Production-ready monitoring stack implementing the **Golden Rule**: "Measure first, optimize second"

**Stack**:

- **Prometheus**: Metrics collection (scrape interval: 15s)
- **Grafana**: Visualization & dashboards
- **Alertmanager**: Alert routing & notifications

---

## Quick Start

### 1. Start Monitoring Stack

```bash
# Start all services including monitoring
docker-compose up -d

# Or start only monitoring stack
docker-compose up -d prometheus grafana alertmanager
```

### 2. Access Dashboards

| Service          | URL                   | Default Credentials |
| ---------------- | --------------------- | ------------------- |
| **Grafana**      | http://localhost:3001 | admin / admin       |
| **Prometheus**   | http://localhost:9090 | N/A                 |
| **Alertmanager** | http://localhost:9093 | N/A                 |

### 3. View Metrics

**Grafana**:

1. Login → http://localhost:3001
2. Navigate to **Dashboards** → **Fabric Management - Overview**
3. View real-time metrics:
   - API Latency (P50, P95)
   - Requests per second (RPS)
   - Error rates
   - DB connection pool usage
   - JVM memory usage
   - Circuit breaker states

**Prometheus**:

- Query interface: http://localhost:9090/graph
- Targets status: http://localhost:9090/targets
- Alerts status: http://localhost:9090/alerts

**Alertmanager**:

- Active alerts: http://localhost:9093/#/alerts
- Alert routing: http://localhost:9093/#/status

---

## Configuration

### Environment Variables

Add to `.env` file:

```bash
# Prometheus
PROMETHEUS_PORT=9090
PROMETHEUS_SCRAPE_INTERVAL=15s
PROMETHEUS_EVAL_INTERVAL=15s

# Grafana
GRAFANA_PORT=3001
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=your-secure-password-here
GRAFANA_ROOT_URL=http://localhost:3001
GRAFANA_LOG_LEVEL=info

# Alertmanager
ALERTMANAGER_PORT=9093

# Alert notifications (optional)
ALERT_SMTP_HOST=smtp.gmail.com
ALERT_SMTP_PORT=587
ALERT_EMAIL_FROM=alerts@fabricmanagement.com
ALERT_SMTP_USERNAME=your-email@gmail.com
ALERT_SMTP_PASSWORD=your-app-password
ALERT_SLACK_WEBHOOK=https://hooks.slack.com/services/YOUR/WEBHOOK/URL

# Alert timing
ALERT_GROUP_WAIT=30s
ALERT_GROUP_INTERVAL=5m
ALERT_REPEAT_INTERVAL=4h
```

### File Structure

```
monitoring/
├── prometheus/
│   ├── prometheus.yml          # Scrape configs
│   └── alerts.yml              # Alert rules
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── prometheus.yml  # Auto-provision Prometheus datasource
│   │   └── dashboards/
│   │       └── default.yml     # Auto-load dashboards
│   └── dashboards/
│       └── fabric-overview.json # Main dashboard
└── alertmanager/
    └── alertmanager.yml        # Alert routing config
```

---

## Key Metrics

### Golden Metrics (RED Method)

| Metric             | Query                                                                                                                  | Threshold | Severity |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------- | --------- | -------- |
| **Rate** (RPS)     | `sum(rate(http_server_requests_seconds_count[1m]))`                                                                    | N/A       | Info     |
| **Errors** (%)     | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | >1%       | Critical |
| **Duration** (P95) | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))`                                 | >500ms    | Warning  |

### Infrastructure Metrics

| Metric             | Query                                                                    | Threshold |
| ------------------ | ------------------------------------------------------------------------ | --------- |
| **DB Pool Usage**  | `hikaricp_connections_active / hikaricp_connections_max`                 | >80%      |
| **JVM Heap**       | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` | >85%      |
| **Cache Hit Rate** | `sum(rate(cache_gets{result="hit"}[5m])) / sum(rate(cache_gets[5m]))`    | <70%      |

---

## Alert Rules

### Severity Levels

| Severity     | Response Time | Notification  |
| ------------ | ------------- | ------------- |
| **Critical** | Immediate     | Email + Slack |
| **Warning**  | 30 minutes    | Slack         |
| **Info**     | Daily digest  | Email         |

### Key Alerts

**Performance**:

- `HighLatencyP95`: P95 >500ms for 5 minutes
- `VeryHighLatencyP99`: P99 >1s for 3 minutes
- `HighErrorRate`: Error rate >1% for 2 minutes
- `SlowRequests`: P50 >1s for 3 minutes

**Database**:

- `DatabaseConnectionPoolHigh`: >80% for 5 minutes
- `DatabaseConnectionPoolExhausted`: 100% for 1 minute
- `SlowDatabaseQueries`: P95 >100ms for 5 minutes

**Resilience**:

- `CircuitBreakerOpen`: Circuit breaker OPEN for >1 minute
- `HighRetryRate`: >5 retries/sec for 5 minutes

**Resources**:

- `HighJVMMemoryUsage`: Heap >85% for 5 minutes
- `CriticalJVMMemoryUsage`: Heap >95% for 1 minute

---

## Grafana Dashboards

### Fabric Management - Overview

**Panels**:

1. **Service Health** (top row)

   - API Gateway status
   - User Service status
   - Company Service status
   - Contact Service status

2. **API Latency** (P50 & P95)

   - Real-time latency tracking
   - Per-service breakdown
   - Threshold lines (200ms warning, 500ms critical)

3. **Requests Per Second**

   - Traffic monitoring
   - Per-service RPS
   - Identifies traffic spikes

4. **HTTP Status Codes**

   - Success rate (2xx)
   - Client errors (4xx)
   - Server errors (5xx)

5. **Database Connection Pool**

   - Active connections
   - Pool utilization %
   - Identifies connection leaks

6. **JVM Memory Usage**

   - Heap utilization
   - Garbage collection impact
   - Memory leak detection

7. **Circuit Breaker States**
   - CLOSED (green) - normal
   - HALF_OPEN (yellow) - testing
   - OPEN (red) - failing

---

## Usage Examples

### Find Slow Endpoints

**Prometheus Query**:

```promql
# Top 10 slowest endpoints (P95)
topk(10, histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[5m]))
  by (service, uri, le)))
```

**Grafana**:

1. Go to **Explore**
2. Enter query above
3. View table or graph
4. Identify bottlenecks

### Monitor Specific Service

**Filter by service**:

```promql
# User service latency
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{service="user-service"}[5m]))
  by (uri, le))
```

### Check Cache Performance

```promql
# Cache hit rate per service
sum(rate(cache_gets{result="hit"}[5m])) by (service) /
sum(rate(cache_gets[5m])) by (service)
```

### Database Connection Health

```promql
# Connection pool usage
hikaricp_connections_active / hikaricp_connections_max

# Pending connections (waiting for connection)
hikaricp_connections_pending
```

---

## Alert Configuration

### Email Alerts (Production)

Edit `monitoring/alertmanager/alertmanager.yml`:

```yaml
receivers:
  - name: "critical-receiver"
    email_configs:
      - to: "devops@fabricmanagement.com"
        headers:
          Subject: "[CRITICAL] {{ .GroupLabels.service }}: {{ .GroupLabels.alertname }}"
```

### Slack Alerts (Production)

1. Create Slack Incoming Webhook: https://api.slack.com/messaging/webhooks
2. Add to `.env`:
   ```bash
   ALERT_SLACK_WEBHOOK=https://hooks.slack.com/services/YOUR/WEBHOOK/URL
   ```
3. Edit `alertmanager.yml`:
   ```yaml
   receivers:
     - name: "critical-receiver"
       slack_configs:
         - channel: "#alerts-critical"
           title: "[CRITICAL] {{ .GroupLabels.alertname }}"
           text: "{{ range .Alerts }}{{ .Annotations.description }}{{ end }}"
           send_resolved: true
   ```

### Test Alerts

```bash
# Trigger test alert (Prometheus)
curl -X POST http://localhost:9090/-/reload

# View alerts
curl http://localhost:9090/api/v1/alerts

# View Alertmanager status
curl http://localhost:9093/api/v1/alerts
```

---

## Troubleshooting

### Prometheus Not Scraping

**Check targets**:

```bash
# View scrape targets
curl http://localhost:9090/api/v1/targets | jq

# Expected: all targets UP
```

**Common issues**:

- Service not exposing `/actuator/prometheus` endpoint
- Network connectivity (Docker network)
- Service not started yet (check depends_on)

**Fix**:

```bash
# Verify service exposes metrics
curl http://localhost:8081/actuator/prometheus

# Check Prometheus logs
docker logs fabric-prometheus
```

### Grafana Dashboard Empty

**Check datasource**:

1. Grafana → Configuration → Data Sources
2. Verify Prometheus datasource configured
3. Test connection → "Data source is working"

**Re-provision**:

```bash
# Restart Grafana to reload provisioning
docker-compose restart grafana
```

### Alerts Not Firing

**Check alert rules**:

```bash
# View alert rules in Prometheus
curl http://localhost:9090/api/v1/rules | jq

# Check if alert is pending/firing
curl http://localhost:9090/api/v1/alerts | jq
```

**Manual trigger** (for testing):

```promql
# Manually set metric to trigger alert
# Example: Simulate high latency
rate(http_server_requests_seconds_count[5m]) > 0
```

---

## Best Practices

### 1. Measure First, Optimize Second

**Workflow**:

```
Day 1: Start monitoring stack
Day 2-7: Collect baseline metrics
Day 8: Analyze bottlenecks in Grafana
Day 9+: Optimize based on DATA, not assumptions
```

**Anti-Pattern**: ❌ Optimize blind → guesswork
**Best Practice**: ✅ Monitor → measure → optimize

### 2. Alert Fatigue Prevention

**Rules**:

- ✅ Only alert on actionable metrics
- ✅ Use `for: 5m` to prevent flapping
- ✅ Group related alerts
- ✅ Set appropriate repeat intervals (4h not 5m)

**Example**:

```yaml
# ❌ BAD: Alert on every spike
- alert: HighLatency
  expr: http_request_duration_seconds{quantile="0.95"} > 0.2
  # Will fire constantly

# ✅ GOOD: Alert on sustained high latency
- alert: HighLatency
  expr: http_request_duration_seconds{quantile="0.95"} > 0.5
  for: 5m # Must persist 5 minutes
```

### 3. Dashboard Organization

**Structure**:

```
Overview Dashboard (this file)
  ├─ Service Health (top)
  ├─ Golden Metrics (API latency, RPS, errors)
  ├─ Infrastructure (DB, JVM, cache)
  └─ Resilience (circuit breakers)

Service-Specific Dashboards (future)
  ├─ User Service Deep Dive
  ├─ Company Service Deep Dive
  └─ Fiber Service Deep Dive
```

### 4. Retention Policy

**Prometheus**:

```
retention: 30 days (configurable)
Rationale: Balance storage vs historical analysis
Production: 90 days recommended
```

**Grafana**:

```
Dashboards: Version controlled (JSON in git)
Data: Prometheus handles storage
Snapshots: Create before major changes
```

---

## Performance Impact

### Monitoring Overhead

| Component                  | CPU   | Memory | Network                    |
| -------------------------- | ----- | ------ | -------------------------- |
| **Prometheus scraping**    | <1%   | ~256MB | ~10KB/sec per service      |
| **Grafana**                | <0.5% | ~128MB | Minimal (user-triggered)   |
| **Alertmanager**           | <0.1% | ~64MB  | Minimal (on alerts)        |
| **Service metrics export** | <0.5% | ~20MB  | Passive (prometheus pulls) |

**Total overhead**: <2% CPU, ~500MB memory

**Trade-off**: Minimal overhead for **invaluable visibility**

---

## Roadmap

### Phase 1 (Current) ✅

- [x] Prometheus scraping all services
- [x] Basic Grafana dashboard (overview)
- [x] Alert rules (performance, health, database)
- [x] Alertmanager setup

### Phase 2 (Q2 2025)

- [ ] Service-specific dashboards (deep dive per service)
- [ ] SLO/SLI tracking dashboards
- [ ] Email + Slack alert integration
- [ ] Custom business metrics dashboards

### Phase 3 (Q3 2025)

- [ ] PostgreSQL exporter integration
- [ ] Redis exporter integration
- [ ] Kafka exporter integration
- [ ] Distributed tracing (OpenTelemetry + Jaeger)

---

## Golden Rule in Action

### Before Monitoring:

```
Developer: "API is slow"
Team: "Which endpoint?"
Developer: "Not sure..."
Team: "How slow?"
Developer: "Feels like 1-2 seconds?"
Team: "Let's add cache everywhere!"  ← BLIND OPTIMIZATION
```

### After Monitoring:

```
Grafana Alert: "P95 latency >500ms on POST /api/companies"
Team opens dashboard:
  - DB query: 400ms (90% of latency)
  - Culprit: Missing index on companies.tax_id
Team: "Add index on tax_id"  ← DATA-DRIVEN OPTIMIZATION

Result: P95 drops from 500ms → 50ms
```

**Moral**: Monitoring turns guesswork into precision engineering.

---

## Quick Reference

### Useful PromQL Queries

```promql
# Top 10 slowest endpoints
topk(10, histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (service, uri, le)))

# Error rate by service
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)

# RPS by service
sum(rate(http_server_requests_seconds_count[1m])) by (service)

# DB connection pool saturation
hikaricp_connections_active / hikaricp_connections_max

# JVM heap usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# Cache hit rate
sum(rate(cache_gets{result="hit"}[5m])) / sum(rate(cache_gets[5m]))

# Circuit breaker states (0=CLOSED, 1=HALF_OPEN, 2=OPEN)
resilience4j_circuitbreaker_state
```

---

**Version**: 1.0  
**Last Updated**: 2025-10-20  
**Author**: Fabric Management Team  
**Golden Rule**: Measure First, Optimize Second 📊
