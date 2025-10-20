# Monitoring Stack Environment Variables

## Add to `.env` file:

```bash
# =============================================================================
# MONITORING STACK (Prometheus + Grafana + Alertmanager)
# =============================================================================

# Prometheus
PROMETHEUS_PORT=9090
PROMETHEUS_SCRAPE_INTERVAL=15s
PROMETHEUS_EVAL_INTERVAL=15s
ENVIRONMENT=development

# Grafana
GRAFANA_PORT=3001
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin
GRAFANA_ROOT_URL=http://localhost:3001
GRAFANA_LOG_LEVEL=info

# Alertmanager
ALERTMANAGER_PORT=9093

# Alert Notifications (OPTIONAL - for production)
ALERT_SMTP_HOST=smtp.gmail.com
ALERT_SMTP_PORT=587
ALERT_EMAIL_FROM=alerts@fabricmanagement.com
ALERT_SMTP_USERNAME=your-email@gmail.com
ALERT_SMTP_PASSWORD=your-app-specific-password
ALERT_SLACK_WEBHOOK=https://hooks.slack.com/services/YOUR/WEBHOOK/URL

# Alert Timing
ALERT_GROUP_WAIT=30s
ALERT_GROUP_INTERVAL=5m
ALERT_REPEAT_INTERVAL=4h

# Alert Recipients
ALERT_EMAIL_CRITICAL=devops@fabricmanagement.com
ALERT_EMAIL_WARNING=team@fabricmanagement.com
ALERT_EMAIL_INFO=monitoring@fabricmanagement.com
ALERT_EMAIL_SECURITY=security@fabricmanagement.com
```

## Production Alert Setup

### 1. Email Alerts

Edit `monitoring/alertmanager/alertmanager.yml`:

```yaml
receivers:
  - name: "critical-receiver"
    email_configs:
      - to: "${ALERT_EMAIL_CRITICAL}"
        headers:
          Subject: "[CRITICAL] {{ .GroupLabels.service }}: {{ .GroupLabels.alertname }}"
```

### 2. Slack Alerts

1. Create webhook: https://api.slack.com/messaging/webhooks
2. Add to `.env`: `ALERT_SLACK_WEBHOOK=https://hooks.slack.com/...`
3. Uncomment Slack config in `alertmanager.yml`
