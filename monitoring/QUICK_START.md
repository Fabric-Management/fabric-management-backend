# Monitoring Stack - Quick Start Guide

## 🚀 5-Minute Setup

### Golden Rule

> **"Measure First, Optimize Second"**
>
> This monitoring stack lets you SEE what's slow before you optimize.

---

## Step 1: Start Monitoring Stack (1 minute)

```bash
# From project root
cd /Users/user/Coding/fabric-management/fabric-management-backend

# Start all services (including monitoring)
docker-compose up -d

# Or start only monitoring stack
docker-compose up -d prometheus grafana alertmanager
```

**Wait for**: All containers healthy (~30 seconds)

```bash
# Check status
docker-compose ps

# Expected output:
# fabric-prometheus     ... Up (healthy)
# fabric-grafana        ... Up (healthy)
# fabric-alertmanager   ... Up (healthy)
```

---

## Step 2: Access Grafana (30 seconds)

### Open Grafana

```bash
# Open in browser
open http://localhost:3001

# Or manually navigate to:
http://localhost:3001
```

### Login

```
Username: admin
Password: admin
```

**First-time**: Grafana will ask to change password (skip for development)

---

## Step 3: View Dashboard (30 seconds)

### Navigate to Dashboard

1. Click **☰** (hamburger menu) → **Dashboards**
2. Click **"Fabric Management - Overview"**

### What You'll See

**7 Panels**:

1. ✅ **Service Health** - Green = UP, Red = DOWN
2. 📊 **API Latency** - P50 & P95 in real-time
3. 📈 **Requests/Sec** - Traffic per service
4. 🎯 **HTTP Status** - 2xx/4xx/5xx distribution
5. 🗄️ **DB Pool** - Connection usage (alert at 80%)
6. 💾 **JVM Memory** - Heap usage (alert at 85%)
7. 🔴 **Circuit Breakers** - CLOSED/OPEN states

**Auto-refresh**: Every 10 seconds

---

## Step 4: Generate Some Traffic (2 minutes)

### Run Performance Test

```bash
# Terminal 1: Start services (if not running)
docker-compose up -d

# Terminal 2: Run fiber service perf test
cd services/fiber-service
export PATH="/usr/local/bin:$PATH"
mvn -Dtest=FiberApiPerfIT test -Djacoco.skip=true
```

**What happens**:

- 50 CREATE requests
- 50 GET requests
- 50 SEARCH requests
- Metrics sent to Prometheus
- Grafana shows spike in "API Latency" panel

### Watch in Grafana

1. Go back to Grafana dashboard
2. Watch **API Latency** panel
3. See **Requests/Sec** spike
4. Check **DB Pool** usage increase

---

## Step 5: Explore Metrics (2 minutes)

### Prometheus Queries

**Open Prometheus**:

```bash
open http://localhost:9090
```

**Try Queries**:

```promql
# API latency P95 (all services)
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (service, uri, le))

# Requests per second
sum(rate(http_server_requests_seconds_count[1m])) by (service)

# DB connection pool usage
hikaricp_connections_active / hikaricp_connections_max

# JVM heap usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# Circuit breaker states (0=CLOSED, 2=OPEN)
resilience4j_circuitbreaker_state
```

**Visualization**:

- Click **Graph** tab
- Adjust time range (last 1h, last 5m, etc.)
- See real-time updates

---

## What's Next?

### Daily Usage

**Morning Check** (2 minutes):

```bash
# Open Grafana
open http://localhost:3001

# Check:
✓ All services green (UP)
✓ P95 latency < 500ms
✓ Error rate < 1%
✓ DB pool < 80%
✓ JVM heap < 85%
```

**Investigate Issues**:

```bash
# If P95 > 500ms on specific endpoint:
1. Note the endpoint (e.g., POST /api/companies)
2. Go to Prometheus → Query:
   histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/companies"}[5m]))
3. Check DB pool usage during that time
4. Check application logs
5. Identify bottleneck
6. Apply targeted fix
7. Monitor improvement in Grafana
```

### Enable Alerts (Production)

**Edit** `/monitoring/alertmanager/alertmanager.yml`:

```yaml
receivers:
  - name: "critical-receiver"
    email_configs:
      - to: "your-email@company.com"
        headers:
          Subject: "[CRITICAL] {{ .GroupLabels.alertname }}"
```

**Or Slack**:

```yaml
slack_configs:
  - channel: "#alerts-critical"
    webhook_url: "https://hooks.slack.com/services/YOUR/WEBHOOK"
```

**Reload Alertmanager**:

```bash
docker-compose restart alertmanager
```

---

## Troubleshooting

### Grafana Shows "No Data"

**Check**:

```bash
# 1. Is Prometheus scraping?
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'

# Expected: all targets "up"

# 2. Are services exposing metrics?
curl http://localhost:8081/actuator/prometheus | head -20

# Expected: lots of metrics

# 3. Restart Grafana
docker-compose restart grafana
```

### Alerts Not Showing

```bash
# Check Prometheus alerts
curl http://localhost:9090/api/v1/alerts | jq

# Check Alertmanager
curl http://localhost:9093/api/v1/alerts | jq

# Reload configs
docker-compose restart prometheus alertmanager
```

### Dashboard Not Loading

```bash
# Check Grafana logs
docker logs fabric-grafana

# Re-provision datasources
docker-compose restart grafana

# Wait 30 seconds, refresh browser
```

---

## Useful Commands

```bash
# Start monitoring only
docker-compose up -d prometheus grafana alertmanager

# Stop monitoring
docker-compose stop prometheus grafana alertmanager

# View logs
docker logs fabric-prometheus
docker logs fabric-grafana
docker logs fabric-alertmanager

# Restart to reload configs
docker-compose restart prometheus    # Reload prometheus.yml, alerts.yml
docker-compose restart alertmanager  # Reload alertmanager.yml
docker-compose restart grafana       # Reload dashboards

# Remove all monitoring data (fresh start)
docker-compose down
docker volume rm fabric-management-backend_prometheus_data
docker volume rm fabric-management-backend_grafana_data
docker volume rm fabric-management-backend_alertmanager_data
docker-compose up -d
```

---

## URLs Summary

| Service          | URL                   | Credentials |
| ---------------- | --------------------- | ----------- |
| **Grafana**      | http://localhost:3001 | admin/admin |
| **Prometheus**   | http://localhost:9090 | None        |
| **Alertmanager** | http://localhost:9093 | None        |
| **API Gateway**  | http://localhost:8080 | JWT token   |

---

## Golden Rule in Action

### Scenario: "Fiber Service is slow"

**Without Monitoring** (❌):

```
You: "It feels slow..."
Team: "How slow?"
You: "Maybe 1-2 seconds?"
Team: "Which endpoint?"
You: "Not sure... all of them?"
Team: "Let's add cache everywhere!" ← BLIND GUESS
```

**With Monitoring** (✅):

```
1. Open Grafana → http://localhost:3001
2. See "API Latency" panel
   Finding: POST /fibers P95=600ms (others <100ms)
3. Prometheus query:
   histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{service="fiber-service",uri="/api/fibers"}[5m]))
   Finding: Consistently high (not spike)
4. Check "DB Pool" panel
   Finding: Pool at 30% (not DB connection issue)
5. Check logs:
   Finding: Query taking 500ms (missing index)
6. Fix: CREATE INDEX idx_fibers_tenant ON fibers(tenant_id);
7. Grafana: P95 drops 600ms → 40ms
8. Success: 93% improvement in 30 minutes
```

**Takeaway**: Monitoring = **Precision**, Not Guessing = **Waste**

---

## Next Steps

1. ✅ **Run for 1 week** - Establish baselines
2. 📊 **Review daily** - Morning health check
3. 🔍 **Analyze patterns** - Weekday vs weekend, peak hours
4. 🎯 **Optimize** - Based on DATA from Grafana
5. 📈 **Validate** - Before/after comparison in dashboard

---

**Time to Value**: 5 minutes  
**Setup Effort**: Minimal (configs included)  
**ROI**: Infinite (prevents days of blind debugging)

**Golden Rule**: Measure First, Optimize Second 📊
