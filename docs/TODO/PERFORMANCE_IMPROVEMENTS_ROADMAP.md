# Performance Improvements Roadmap

## Overview

Bu dokümanda **ücretsiz, pragmatik ve gerekli** performans iyileştirmeleri listelenmiştir.

**İlke**: YAGNI (You Aren't Gonna Need It) + KISS (Keep It Simple, Stupid)

**Maliyet Kısıtı**: Sadece **open source** araçlar, **$0 aylık maliyet**

---

## Phase 1: Gözlem & Baseline (1 hafta - Pasif)

### ✅ Tamamlandı

- [x] Prometheus + Grafana + Alertmanager setup
- [x] 40+ alert rules configured
- [x] Overview dashboard (7 panels)
- [x] All services scraping (15s interval)

### 📊 TODO: Baseline Toplama

**Süre**: 1 hafta (pasif gözlem)  
**Effort**: 0 saat (sadece dashboard kontrol)  
**Maliyet**: $0

**Aksiyon**:

```bash
# Her gün 5 dakika:
1. open http://localhost:3001
2. "Fabric Management - Overview" dashboard'u kontrol et
3. Metrikleri not et:
   - P50/P95 latency per service
   - RPS (peak hours vs off-peak)
   - Error rates
   - DB pool peak usage
   - JVM heap trend
```

**Çıktı**: Baseline dokümanı

```
Service         | P50    | P95    | Peak RPS | Avg RPS
----------------|--------|--------|----------|--------
API Gateway     | 10ms   | 25ms   | 50       | 10
User Service    | 50ms   | 120ms  | 30       | 5
Company Service | 45ms   | 150ms  | 20       | 3
Fiber Service   | 67ms   | 131ms  | 15       | 2
```

**Tarih**: Hemen başla (1 hafta pasif)

---

## Phase 2: Hızlı İyileştirmeler (Q2 2025)

### 1. Custom Async Executor Pool

**Durum**: TODO  
**Öncelik**: Low (best practice)  
**Effort**: 3 saat  
**Maliyet**: $0

**Sebep**:

- Şu anda unbounded thread pool (risk düşük ama best practice değil)
- Tek @Async kullanımı var (policy audit)
- Bounded pool = resource control

**Implementasyon**:

```java
// shared-infrastructure/src/main/java/com/fabricmanagement/shared/infrastructure/config/AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);               // 2 thread (low, audit only)
        executor.setMaxPoolSize(5);                // Max 5 thread
        executor.setQueueCapacity(50);             // 50 task queue
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

**Validation**:

- Prometheus metric: `executor_active_threads`, `executor_queue_size`
- Grafana panel: Async executor usage

**Tarih**: Q2 2025 (Mart-Nisan)

---

### 2. Service-Specific Grafana Dashboards

**Durum**: TODO  
**Öncelik**: Medium  
**Effort**: 8 saat (1 gün)  
**Maliyet**: $0

**Sebep**:

- Overview dashboard yüzeysel
- Service-specific deep dive gerekli (per-endpoint latency, etc.)

**Dashboards**:

1. **User Service Deep Dive**

   - Login endpoint latency
   - Tenant onboarding flow
   - JWT token generation time
   - Failed login attempts

2. **Company Service Deep Dive**

   - Duplicate detection latency
   - Similarity search performance
   - Feign call breakdown (user + contact service)

3. **Fiber Service Deep Dive**
   - CREATE/GET/SEARCH latency trends
   - Cache hit rate per operation
   - Kafka event publishing latency

**Template**: Copy `/monitoring/grafana/dashboards/fabric-overview.json` and customize

**Tarih**: Q2 2025 (Nisan-Mayıs)

---

### 3. k6 Load Testing

**Durum**: TODO  
**Öncelik**: High (production öncesi kritik)  
**Effort**: 24 saat (3 gün)  
**Maliyet**: $0 (k6 open source)

**Sebep**:

- Capacity planning gerekli
- Saturation point bilinmiyor
- Production'a çıkmadan önce **must-have**

**Test Scenarios**:

```javascript
// k6-smoke-test.js (5 dakika)
export const options = {
  vus: 10,
  duration: "30s",
};

// k6-load-test.js (30 dakika)
export const options = {
  stages: [
    { duration: "5m", target: 50 }, // Ramp-up
    { duration: "20m", target: 50 }, // Sustained load
    { duration: "5m", target: 0 }, // Cool-down
  ],
  thresholds: {
    "http_req_duration{p(95)}": ["<500"],
    http_req_failed: ["<0.01"],
  },
};

// k6-stress-test.js (15 dakika)
export const options = {
  stages: [
    { duration: "2m", target: 100 },
    { duration: "5m", target: 200 }, // Find breaking point
    { duration: "3m", target: 300 },
    { duration: "5m", target: 0 },
  ],
};
```

**Deliverables**:

- Smoke test (CI/CD'de her deploy)
- Load test (haftalık)
- Stress test (aylık)
- Capacity report: "X RPS'e kadar handle edebiliriz"

**Tarih**: Q2 2025 (Mayıs - Production öncesi)

---

### 4. Email Alert Integration

**Durum**: TODO  
**Öncelik**: Medium  
**Effort**: 4 saat  
**Maliyet**: $0 (Gmail SMTP ücretsiz - 500 email/day limit)

**Sebep**:

- Şu anda alerts sadece Alertmanager UI'da
- Email notification production'da kritik

**Setup**:

```yaml
# monitoring/alertmanager/alertmanager.yml
global:
  smtp_smarthost: "smtp.gmail.com:587"
  smtp_from: "alerts@fabricmanagement.com"
  smtp_auth_username: "your-email@gmail.com"
  smtp_auth_password: "your-gmail-app-password" # Not normal password!
  smtp_require_tls: true

receivers:
  - name: "critical-receiver"
    email_configs:
      - to: "devops@fabricmanagement.com"
        headers:
          Subject: "[CRITICAL] {{ .GroupLabels.service }}: {{ .GroupLabels.alertname }}"
        html: |
          <h2>{{ .GroupLabels.alertname }}</h2>
          <p><strong>Service:</strong> {{ .GroupLabels.service }}</p>
          <p><strong>Description:</strong> {{ .Annotations.description }}</p>
          <p><strong>Time:</strong> {{ .StartsAt }}</p>
```

**Gmail App Password**:

1. Google Account → Security → 2-Step Verification
2. App Passwords → Generate
3. Use generated password in `smtp_auth_password`

**Tarih**: Q2 2025 (Production'a yakın)

---

## ❌ YAPILMAYACAKLAR (Ücretli veya YAGNI)

### Ücretli Olanlar (Budget yok)

| Tool            | Aylık Maliyet | Sebep                          | Alternatif (Ücretsiz)         |
| --------------- | ------------- | ------------------------------ | ----------------------------- |
| **Elastic APM** | $100-500      | Grafana + Prometheus zaten var | ✅ Grafana (kullanıyoruz)     |
| **Datadog**     | $50-300/host  | Over-engineering               | ✅ Prometheus (kullanıyoruz)  |
| **New Relic**   | $100-400      | Gereksiz duplicate             | ✅ Grafana (kullanıyoruz)     |
| **Paid Slack**  | $8/user/ay    | Free tier yeterli              | ✅ Slack Free (500 email/gün) |

### Over-Engineering (YAGNI)

| İyileştirme                   | Sebep                                                        | Karar                                 |
| ----------------------------- | ------------------------------------------------------------ | ------------------------------------- |
| **Hibernate 2nd-Level Cache** | Spring @Cacheable yeterli, iki cache layer = complexity hell | ❌ YAPMA                              |
| **Bulkhead Pattern**          | Circuit breaker + retry zaten var, marginal gain             | ⏸️ ERTELE (yüksek concurrency olunca) |
| **OpenTelemetry + Jaeger**    | 6 service için overkill, correlation ID yeterli              | ⏸️ ERTELE (10+ service olunca)        |

### Henüz Erken (Scale Gerekince)

| İyileştirme                | Ne Zaman Gerekir                          | Şimdilik  |
| -------------------------- | ----------------------------------------- | --------- |
| **Database Read Replicas** | Read:Write = 1000:1 veya 100+ RPS         | ⏸️ ERTELE |
| **Redis Cluster**          | Single Redis fail ediyor veya >10GB cache | ⏸️ ERTELE |
| **Kafka Multi-Broker**     | >1000 events/sec veya HA gereksinimi      | ⏸️ ERTELE |

---

## Öncelik Sıralaması

### Hemen (Önümüzdeki 1 Hafta)

1. ✅ **Monitoring Baseline Toplama** (0 saat - pasif)
   - Grafana'yı her gün kontrol et
   - Baseline metrikleri not et

### Kısa Vade (1-2 Ay)

2. 🔧 **Custom Async Executor** (3 saat)

   - Basit best practice
   - Resource control

3. 📊 **Service Dashboards** (8 saat)
   - Deep dive per service
   - Better visibility

### Orta Vade (2-4 Ay)

4. 🧪 **k6 Load Testing** (24 saat)

   - Production öncesi kritik
   - Capacity planning

5. 📧 **Email Alerts** (4 saat)
   - Production monitoring
   - Gmail SMTP (ücretsiz)

---

## Success Criteria

### Baseline Phase (1 hafta)

**Tamamlanma Kriterleri**:

- [ ] Grafana dashboard 7 gün kesintisiz çalıştı
- [ ] Her servis için baseline metrics kaydedildi
- [ ] Peak hours tespit edildi
- [ ] Slow endpoints listelendi

**Deliverable**: `docs/analysis/BASELINE_METRICS.md`

### Custom Async Executor

**Tamamlanma Kriterleri**:

- [ ] `AsyncConfig.java` oluşturuldu
- [ ] Thread pool bounded (core=2, max=5, queue=50)
- [ ] Prometheus metrics exposed
- [ ] Grafana panel eklendi (async executor usage)

### k6 Load Testing

**Tamamlanma Kriterleri**:

- [ ] Smoke test yazıldı (30s, 10 VUs)
- [ ] Load test yazıldı (30min, 50 VUs sustained)
- [ ] Stress test yazıldı (find breaking point)
- [ ] CI/CD'ye entegre edildi (smoke test her deploy)
- [ ] Capacity report: "X RPS handle edebiliriz"

### Email Alerts

**Tamamlanma Kriterleri**:

- [ ] Gmail App Password oluşturuldu
- [ ] Alertmanager email config yapıldı
- [ ] Test alert gönderildi ve alındı
- [ ] Critical alerts email'e düşüyor

---

## Anti-Patterns (Yapma Listesi)

### ❌ Premature Optimization

```
Durum: Traffic 10 RPS
Öneri: Kafka cluster + read replicas + Redis cluster
Maliyet: $500+ infrastructure + 2 hafta effort
Karar: YAPMA - 100 RPS'e kadar mevcut yeterli
```

### ❌ Tool Addiction

```
Durum: Grafana çalışıyor
Öneri: "Elastic APM da ekleyelim daha güzel UI"
Maliyet: $200/ay + maintenance
Karar: YAPMA - Aynı data, farklı wrapper
```

### ❌ Cache Everywhere

```
Durum: 1 endpoint yavaş
Öneri: "Tüm endpoint'leri cache'leyelim"
Risk: Stale data, invalidation nightmare
Karar: YAPMA - Sadece bottleneck'i cache'le
```

---

## Budget Reality Check

### Current Situation

**Ücretsiz Kullanılanlar** (✅):

- PostgreSQL (open source)
- Redis (open source)
- Kafka (open source)
- Spring Boot (open source)
- Prometheus + Grafana (open source)
- k6 (open source)

**Total Monthly Cost**: **$0**

### Ücretli Alternatifler (❌ Budget yok)

| Tool        | Aylık | Yıllık | Alternatif (Ücretsiz)     |
| ----------- | ----- | ------ | ------------------------- |
| Datadog     | $300  | $3,600 | Prometheus + Grafana ✅   |
| New Relic   | $200  | $2,400 | Prometheus + Grafana ✅   |
| Elastic APM | $150  | $1,800 | Prometheus + Grafana ✅   |
| AWS RDS     | $100  | $1,200 | Self-hosted PostgreSQL ✅ |
| Redis Cloud | $50   | $600   | Self-hosted Redis ✅      |

**Total Savings**: ~$800/ay = ~$9,600/yıl

**Karar**: Open source stack ile devam, **$0 maliyet**

---

## Pragmatic Roadmap

### Q1 2025 (Ocak-Mart) - Monitoring & Baseline

- [x] Monitoring stack setup ✅ DONE
- [ ] 1 hafta baseline toplama (pasif)
- [ ] Bottleneck'leri tespit et (Grafana'dan)
- [ ] Optimization backlog oluştur (data-driven)

**Deliverables**:

- Baseline metrics dokümanı
- Bottleneck listesi (öncelikli)

### Q2 2025 (Nisan-Haziran) - Tactical Improvements

- [ ] Custom async executor (3 saat)
- [ ] Service-specific dashboards (8 saat)
- [ ] Email alert integration (4 saat)
- [ ] k6 smoke test (8 saat)
- [ ] Data-driven optimizations (baseline'dan çıkan)

**Deliverables**:

- Async executor metrics
- 3 service-specific dashboards
- Email alerts aktif
- k6 smoke test CI/CD'de

### Q3 2025 (Temmuz-Eylül) - Load Testing & Validation

- [ ] k6 load test (8 saat)
- [ ] k6 stress test (8 saat)
- [ ] Capacity report (4 saat)
- [ ] Performance regression suite (8 saat)

**Deliverables**:

- Capacity planning report
- SLA baselines (99.9% uptime için kaç instance gerekir)
- Load test CI/CD integration

### Q4 2025 (Ekim-Aralık) - Scale Planning

- [ ] Baseline review (quarterly)
- [ ] Scale strategy (horizontal vs vertical)
- [ ] Cost projection (cloud migration ise)

**Karar Noktası**:

- Read replicas gerekli mi? (read traffic'e göre)
- Redis cluster gerekli mi? (cache size'a göre)
- Kafka multi-broker gerekli mi? (event volume'a göre)

---

## Decision Matrix

### Ne Zaman Yap?

| İyileştirme                | Tetikleyici                                | Action                |
| -------------------------- | ------------------------------------------ | --------------------- |
| **Database Read Replicas** | Read:Write > 10:1 veya P95 DB query >100ms | Replica ekle          |
| **Redis Cluster**          | Cache size >5GB veya downtime unacceptable | Cluster setup         |
| **Kafka Multi-Broker**     | Event volume >1000/sec veya HA requirement | Multi-broker          |
| **Bulkhead Pattern**       | Concurrent users >100/service              | Resilience4j bulkhead |
| **OpenTelemetry**          | Service count >10                          | Distributed tracing   |

### Ne Zaman Yapma?

| Anti-Pattern                  | Sebep                                   | Karar                     |
| ----------------------------- | --------------------------------------- | ------------------------- |
| **Hibernate 2nd-Level Cache** | Spring @Cacheable yeterli, complexity++ | ASLA                      |
| **Paid APM**                  | Grafana + Prometheus zaten var          | Budget olsa bile gereksiz |
| **Microservice Explosion**    | 6 service yeterli şimdilik              | 10+ domain olunca         |

---

## Success Metrics

### Phase 1 (Baseline) - Success

- ✅ Monitoring stack 7/24 çalışıyor
- ✅ 0 downtime
- ✅ Metrics toplanıyor (Prometheus 30 gün retention)
- ✅ Grafana dashboard accessible

### Phase 2 (Improvements) - Success

- ✅ P95 latency trend azalıyor (veya stabil)
- ✅ Error rate <1%
- ✅ DB pool usage <70%
- ✅ Cache hit rate >80%
- ✅ Circuit breakers CLOSED

### Phase 3 (Load Testing) - Success

- ✅ Capacity bilinir: "X RPS'e kadar handle ederiz"
- ✅ Breaking point tespit edildi
- ✅ Scale planı hazır (N instance gerekir Y RPS için)

---

## Cost-Benefit Analysis

### Total Investment

**Zaman**:

- Monitoring setup: ✅ DONE (8 saat)
- Baseline toplama: 0 saat (pasif)
- Async executor: 3 saat
- Dashboards: 8 saat
- k6 testing: 24 saat
- Email alerts: 4 saat

**Total**: ~47 saat (1 kişi, 1.5 hafta)

**Para**: **$0** (tüm araçlar open source)

### Expected Benefits

**Önlenen Maliyetler**:

- Production outage önleme: $10,000+/incident
- Blind optimization önleme: 40+ saat wasted effort
- Customer churn önleme: Slow API → users leave
- Developer productivity: 50% faster debugging (Grafana vs logs)

**ROI**: **Infinite** (zero cost, massive benefit)

---

## Roadmap Summary

```
NOW (Completed):
✅ Monitoring stack deployed
✅ 40+ alerts configured
✅ Overview dashboard active

NEXT (1 hafta):
📊 Baseline metrics toplama (pasif)

Q2 2025 (Nisan-Haziran):
🔧 Custom async executor (3h)
📈 Service dashboards (8h)
📧 Email alerts (4h)
🧪 k6 smoke test (8h)

Q3 2025 (Temmuz-Eylül):
🚀 k6 load + stress tests (16h)
📋 Capacity report (4h)

Q4 2025+:
⏸️ Scale infrastructure (gerekirse)
```

**Total Time**: ~50 saat over 6 ay  
**Total Cost**: **$0**  
**Value**: **Production-ready monitoring & optimization**

---

**Prensip**: "Perfect is the enemy of good"  
**Yaklaşım**: Pragmatic, not dogmatic  
**Budget**: $0 (open source only)  
**Timeline**: 6 ay, incremental improvements

**Version**: 1.0  
**Last Updated**: 2025-10-20  
**Owner**: Fabric Management Team
