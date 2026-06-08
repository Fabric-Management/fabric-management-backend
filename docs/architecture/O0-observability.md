# Faz: Observability — Dağıtık Trace + Yapısal Log + Metrik/Alert

> **Amaç:** Her isteği ve her async/dayanıklı-event zincirini uçtan uca izlenebilir kılmak;
> her log satırını tenant + trace + correlation ile etiketlemek; takılı (poison) event'leri
> görünür kılmak. "84 listener'lık akışı ancak trace id ile takip edebilirsin" — baştan
> söylediğimiz şey.
>
> **Sinerji:** P0 (`DomainEvent.tenantId` kontratı) ve E0 (dayanıklı event'ler) üzerine
> oturuyor; correlation, tenantId ile **aynı kalıpla** event payload'ında taşınacak.
>
> **Kalibrasyon notu:** Observability "erken eklenirse bedava, sonradan retrofit edilirse
> işkence". Özellikle **trace propagasyonu** (async + dayanıklı event sınırları) şimdi
> tasarlanmalı — mevcut event zincirine sonradan correlation enjekte etmek tam da o acı türü.
> Ama gold-plating yok: HTTP/DB/JDBC otomatik enstrümante; biz yalnızca **kendi sınırlarımızı**
> (async + durable event) ve birkaç kilit metriği elle kuruyoruz.

---

## Mevcut Durum (Kod Araştırması)

| Bileşen | Durum |
|---------|-------|
| Spring Boot Actuator | ✅ var, expose: health/info/metrics/prometheus |
| Micrometer + Prometheus registry | ✅ var (1.13.0), `/actuator/prometheus` açık |
| `micrometer-tracing-bridge-brave` | ⚠️ var ama **exporter yok** → trace üretiliyor, hiçbir yere gitmiyor |
| Trace sampling config | ❌ yok (default %10 head sampling) |
| **Log pattern** | 🔴 `"%d - %msg%n"` — **traceId/spanId düşürülüyor**, loglar korele edilemiyor |
| JSON/structured log | ❌ yok (logback-spring.xml yok, plain text) |
| tenantId MDC'de | ❌ yok → log satırları tenant-atfedilebilir değil |
| Incomplete-publication metriği (E0 carryover) | ❌ yok → poison event'ler görünmez |

**Çıkarım:** Tracing iskeleti (Micrometer) ve metrik altyapısı (Prometheus) hazır ama
**bağlanmamış** ve log korelasyonu **bozuk**. İş: exporter + log düzeltmesi + kendi
sınırlarımızda propagasyon + E0 metriği.

---

## Tasarım Kararı

| # | Konu | Karar |
|---|------|-------|
| 1 | **Tracer bridge** | Brave → **OTel** (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`). Kullanıcı "OTel" dedi; vendor-neutral, herhangi bir OTel collector'a (Tempo/Jaeger/Grafana) gider |
| 2 | Metrik | Prometheus kalsın (zaten var, `/actuator/prometheus`) |
| 3 | Log | **JSON structured** (logstash-logback-encoder) + traceId/spanId/tenantId/correlationId MDC'de. Dev profilinde pretty, prod'da JSON |
| 4 | Backend hedefi | OTLP → collector (deploy-infra; kod yalnızca OTLP export eder — Tempo/Jaeger seçimi sonra) |
| 5 | Correlation taşıma | `DomainEvent`'e `correlationId` ekle (tenantId ile aynı kalıp) → dayanıklı/resubmit edilen event'ler de izlenebilir |

---

## Görevler

### O0 — Stack kararı (ADR)
Brave→OTel swap, OTLP export, sampling stratejisi (dev %100, prod ~%10 + hata her zaman
örneklensin), JSON log profili. ADR'ye yaz.

### O1 — Trace temeli
- `pom.xml`: `micrometer-tracing-bridge-brave` → `micrometer-tracing-bridge-otel` +
  `io.opentelemetry:opentelemetry-exporter-otlp`.
- `application.yml`: `management.tracing.sampling.probability`, OTLP endpoint (env), service name.
- HTTP/DB/repository otomatik enstrümante (Micrometer Observation) — ek kod yok.

### O2 — Log korelasyonu (🔴 kritik düzeltme + JSON)
- **Log pattern düzeltmesi:** `console`/`file` pattern'ine `%mdc` / Spring Boot correlation
  pattern'i ekle → traceId/spanId her satırda. (Şu an düşürülüyor.)
- **JSON structured log:** `logback-spring.xml` + `logstash-logback-encoder`; traceId, spanId,
  tenantId, correlationId **alanlar** olarak (grep'lenebilir/sorgulanabilir).
- **tenantId MDC'de:** `TenantContext` set edildiğinde (interceptor / aspect / MTCP yolu)
  `MDC.put("tenantId", ...)`, clear'da kaldır → **her log satırı tenant-atfedilebilir** (P0 sinerjisi).

### O3 — Kendi sınırlarımızda propagasyon (asıl iş; P0+E0 sinerjisi)
- **@Async:** `TenantAwareTaskDecorator` artık trace context'i **de** taşımalı (Micrometer
  `ContextPropagatingTaskDecorator` ile kompoze et ya da observation scope'u capture/restore et).
  Yoksa async iş trace'i kaybeder — tam da tenant context'te çözdüğümüz problem.
- **Dayanıklı event resubmit:** Resubmit saatler sonra / restart sonrası olur → orijinal trace
  gitmiştir. Çözüm: `DomainEvent`'e `correlationId` ekle (tenantId kontratı gibi), ve
  (re)teslimde `TenantRestoringEventListenerAspect`'in kardeşi olarak correlationId'yi MDC'ye
  restore et + orijinale bağlı yeni bir span başlat. Böylece dayanıklı async zincir uçtan uca izlenir.

### O4 — Incomplete-publication metriği + alert (E0 carryover)
- Micrometer gauge: `events.publications.incomplete` = N dakikadan eski incomplete event sayısı
  (Modulith `IncompleteEventPublications` veya `event_publication` sorgusu ile).
- `/actuator/prometheus`'ta açık + dokümante bir alert kuralı (ör. ">0 for 15m" → uyar).
- Böylece E0'ın sessiz sonsuz-retry poison event'leri **görünür** olur.

### O5 — Kilit custom metrikler (kalibre — her şey değil)
Yüksek değerli birkaç tane: dayanıklı event işleme (başarı/hata/retry sayacı), onboarding
süresi, opsiyonel RLS-deny sayacı. HTTP/DB için otomatik enstrümantasyona güven; elle her
metodu enstrümante etme.

### O6 — Korkuluk + doğrulama
- Test: log pattern correlation içeriyor mu (traceId boş değil).
- IT: bir isteğin traceId'si `@Async` event handler'a propagate oluyor mu (handler'ın MDC'sinde
  **aynı** traceId assert et) — async propagasyonun deterministik kanıtı.
- Test: `events.publications.incomplete` metriği expose ediliyor.
- ArchUnit (opsiyonel): yeni `DomainEvent`'ler correlationId taşımalı (tenantId guard'ı gibi).

---

## Open Questions

> [!IMPORTANT]
> **Q1 — Brave→OTel swap onayı:** Kullanıcı "OTel" dedi; mevcut dep Brave. `bridge-otel` +
> OTLP exporter'a geçiyorum. Alternatif: Brave kalıp Zipkin'e export. Önerim: OTel (vendor-neutral).
> Onay?

> [!IMPORTANT]
> **Q2 — Backend hedefi:** Self-hosted olduğun için Grafana stack (Tempo trace + Loki log +
> Prometheus metrik) doğal. Kod sadece OTLP export eder; hedef seçimi deploy-infra. Tempo mu,
> Jaeger mi, yoksa şimdilik sadece OTLP-export edip backend'i sonra mı bağlayalım? Önerim:
> kodu OTLP'ye hazır et, collector'ı sonra.

> [!NOTE]
> **Q3 — JSON log profili:** Dev'de pretty (insan okur), prod'da JSON (makine sorgular).
> Profile-bazlı öneriyorum. Onay?

> [!NOTE]
> **Q4 — Sampling:** Dev %100, prod ~%10 head sampling. "Hatalı trace'ler her zaman örneklensin"
> tail-sampling collector ister (kod değil). Şimdilik head-sampling yeterli mi?

---

## Verification Plan

- **Log korelasyon testi:** traceId/spanId log satırında + JSON alanı olarak mevcut.
- **Async propagasyon IT:** request → `@Async` event handler → handler MDC'sinde **aynı traceId**.
- **Dayanıklı event correlation:** resubmit edilen event'in correlationId'si MDC'ye restore ediliyor.
- **Metrik:** `events.publications.incomplete` + tenant-tagged log + `/actuator/prometheus` çalışıyor.
- **Mevcut suite (720+) yeşil kalmalı** (bridge swap davranışı bozmamalı).

---

## Definition of Done

- [x] O1: OTel bridge + OTLP exporter + sampling config; HTTP/DB otomatik trace'li.
- [x] O2: log pattern traceId/spanId taşıyor; JSON structured log; tenantId MDC'de (her satır tenant-atfedilebilir).
- [x] O3: `@Async` trace propagasyonu + `DomainEvent.correlationId` + resubmit'te restore.
- [x] O4: `events.publications.incomplete` metriği + alert kuralı (E0 poison görünürlüğü).
- [x] O5: birkaç kilit custom metrik (over-instrument yok). Eklenenler:
  - `events.processing.success/failure/duplicate` (Counter) - Idempotent event handler
  - `events.resubmission.job.runs` (Counter) - EventResubmissionJob
  - `tenant.onboarding.duration` (Timer) - TenantOnboardingOrchestrator
  - `security.access.denied` (Counter) - GlobalExceptionHandler
- [ ] O6: log-correlation testi + async-propagasyon IT + metrik expose testi yeşil.
- [x] Mevcut 720+ test yeşil.

---

## P0 + E0 ile Sinerji

- `correlationId`, P0'da kurduğumuz `DomainEvent.tenantId` kontratının ikizi — aynı taşıma,
  aynı restore-aspect kalıbı.
- `tenantId`'yi MDC'ye koymak, P0'ın tenant context'ini observability'ye bağlar: her log,
  hangi tenant'a ait olduğunu söyler.
- O4, E0'ın açık bıraktığı poison-message görünürlüğünü kapatır — fail-closed + görünürlük ilkesi.
