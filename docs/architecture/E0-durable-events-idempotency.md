# Faz: Dayanıklı Event'ler + Idempotency (Transactional Outbox)

> **Amaç:** Modül-arası event teslimini "ateşle-unut, çökerse kaybolur"dan, **commit ile
> aynı transaction'da kalıcılaştırılan + en-az-bir-kez teslim edilen + idempotent tüketilen**
> dayanıklı bir akışa çevirmek. "Sipariş onaylandı ama work order hiç oluşmadı" tipi
> hayalet hataları kökten silmek.
>
> **Bağımlılık:** P0 (RLS izolasyonu) bitti. **Sinerji:** P0'ın `TenantRestoringEventListenerAspect`
> + `DomainEvent.tenantId` kontratı, dayanıklı yeniden-teslimi tenant-güvenli yapan tam da
> bu mekanizma — aşağıda bir uyarıyla (E1).

---

## Mevcut Durum (Kod Araştırması)

| Bulgu | Kanıt / Etki |
|-------|--------------|
| Event'ler %100 in-process | `DomainEventPublisher` → `ApplicationEventPublisher.publishEvent()` (Kafka/externalize yok) |
| 33 `@TransactionalEventListener(AFTER_COMMIT)`, **0** `@ApplicationModuleListener` | Modulith'in dayanıklı listener mekanizması **kullanılmıyor** |
| `spring-modulith-events-jpa` + `public.event_publication` tablosu | Dependency **ve tablo zaten var** — registry boşta, aktive edilmeyi bekliyor |
| Modulith config yok | `republish-outstanding-events-on-restart` ayarı yok → restart'ta kurtarma yok |
| Idempotency yok | Tüketici dedup tablosu/mantığı yok |
| Kafka | Config var, **kullanılmıyor** (`kafkaTemplate.send` / `@KafkaListener` yok) |

**Boşluk:** `AFTER_COMMIT` + `@Async` → business tx commit eder, listener ayrı thread'de sonra
çalışır. Arada çökme = event **kalıcı kayıp**. Hiçbir yerde "bu event işlenmeli" diye bir
kayıt yok.

**Fırsat:** Modulith Event Publication Registry tam olarak bir transactional outbox'tır;
dependency + tablo hazır. Sıfırdan outbox yazmıyoruz — **aktive ediyoruz.**

---

## Tasarım Kararı

| # | Yaklaşım | Karar |
|---|----------|-------|
| 1 | **Modulith Event Publication Registry'yi aktive et** (`@ApplicationModuleListener` + republish-on-restart) | ✅ Seçildi — dependency+tablo hazır, idiomatic, az kod |
| 2 | Elle özel outbox tablosu + poller | ❌ Modulith'in verdiğini yeniden icat etmek |
| 3 | Kafka'ya externalize | ❌ Şimdilik değil — monolith-first; gerçek dış tüketici çıkınca (extraction) açılır. Kafka config dormant kalır |

**Teslim garantisi:** at-least-once (Modulith republish redelivery yapabilir) → tüketiciler
**idempotent olmak zorunda** (E2).

**Senkron vs dayanıklı sınırı:** Her listener outbox'a girmez. Modül-**içi** sıkı tutarlılık
gereken reaksiyonlar senkron/aynı-tx kalır; yalnızca modül-**arası entegrasyon event'leri**
dayanıklı `@ApplicationModuleListener` olur (E0 sınıflandırması).

---

## Görevler

### E0 — Event sınıflandırması (ADR)
33 listener'ı ikiye ayır: **modül-arası entegrasyon event'i** (dayanıklı outbox) vs
**modül-içi reaksiyon** (senkron/aynı-tx kalabilir). Kriter: event bir bounded-context
sınırını geçiyor mu? (ör. `SalesOrderConfirmed` → production = entegrasyon; bir entity'nin
kendi alt-kaydını güncelleyen reaksiyon = içsel.)
*Bitti =* ADR'de her event sınıflı; dayanıklı set net.

### E1 — Modulith Registry'yi aktive et
- Dayanıklı set'teki listener'ları `@TransactionalEventListener(AFTER_COMMIT)+@Async` →
  **`@ApplicationModuleListener`**'a çevir.
- `application.yml`: `spring.modulith.events.republish-outstanding-events-on-restart=true`
  (+ completion mode config).
- `event_publication` zaten `fabric_app`'e DML-grant'li (P0/D1) ve RLS-muaf (T6 allowlist) → uyumlu.

> [!WARNING]
> **🔴 Aspect pointcut tuzağı (P0 sinerjisini kırabilir):** `TenantRestoringEventListenerAspect`
> pointcut'ı `@annotation(...TransactionalEventListener) || @annotation(...EventListener)`.
> `@ApplicationModuleListener` bunlarla **meta-annotated**'tır ama AspectJ `@annotation`
> **meta-annotation'ı görmez**. Yani listener'ları çevirince aspect **eşleşmeyi durdurur** →
> dayanıklı/republish edilen event'lerde tenant context restore edilmez → RLS altında
> sessiz boş okuma / fail-closed yazma. **Çözüm:** pointcut'a açıkça
> `@annotation(org.springframework.modulith.ApplicationModuleListener)` ekle. Bu, E1'in
> en kritik tek satırı.

### E2 — Idempotency (en-az-bir-kez güvenliği)
- `processed_event` tablosu: `(event_id UUID, listener_id TEXT)` üzerinde UNIQUE.
- Handler girişinde: bu (event_id, listener_id) işlenmişse **atla**; değilse işle + kaydet,
  aynı transaction'da. Modulith zaten `listener_id` + event id taşıyor — onu kullan.
- Tercihen küçük bir sarmalayıcı/aspect ile, her handler'da elle değil.
*Bitti =* aynı event iki kez teslim edilince yan etki bir kez oluşur (test E4).

### E3 — Tenant-context + RLS sinerjisi doğrulaması
Republish restart'ta context'siz çalışır → aspect (E1 düzeltmesiyle) payload'dan tenant'ı
restore eder → MTCP doğru tenant'a bind eder → işlem doğru tenant'ta. `processed_event`
tablosunun RLS durumu: tenant_id taşımalı mı (per-tenant dedup) yoksa global mi? (Open Q2.)

### E4 — Dayanıklılık kanıt testi (bu fazın "TenantIsolationIT"i)
- **Crash/recovery:** event publish et, business tx commit olsun, listener çalışmadan
  publication'ı "incomplete" bırak (listener'da hata fırlat / context'i kapat), sonra
  republish'i tetikle → listener **eninde sonunda çalışır**, yan etki **tam bir kez**.
- **Idempotency:** aynı event'i iki kez teslim et → yan etki bir kez.
- **Tenant restore:** republish edilen event doğru tenant context'inde işlenir.
*Bitti =* 3 senaryo da yeşil; outbox kapatılırsa test kırmızıya döner.

### E5 — Korkuluk (makineyle-zorlama)
ArchUnit: modül-arası (dayanıklı set) event handler'ları **`@ApplicationModuleListener`
olmak zorunda** (çıplak `@TransactionalEventListener` değil), ve idempotent-sarmalayıcıdan
geçmeli. Yeni bir agent çıplak async listener eklerse build kırılır.

---

## Open Questions

> [!IMPORTANT]
> **Q1 — Completion mode:** Modulith publication'ları tamamlandığında **siler** mi
> (`DELETE`) yoksa `completion_date` ile **işaretleyip tutar** mı (audit + replay için)?
> Tutmak audit/debug için değerli ama tablo büyür → bir arşiv/purge job gerekir. Önerim:
> işaretle-ve-tut + periyodik purge (ör. 30 gün). Onay?

> [!IMPORTANT]
> **Q2 — `processed_event` tenant-scope:** Dedup tablosu `tenant_id` taşıyıp RLS'e mi
> girsin (tek tip model), yoksa global infra tablosu (event_publication gibi RLS-muaf) mı
> olsun? event_id zaten globalde unique olduğundan global yeterli; ama tek-tip RLS modeli
> için tenant_id eklemek de savunulabilir. Önerim: global + T6 allowlist (event_publication
> ile tutarlı). Tercih?

> [!NOTE]
> **Q3 — Kapsam/sıra:** 33 listener'ı tek seferde mi, yoksa önce kritik iş zincirini
> (sales→production→shipment) çevirip E4 ile kanıtlayıp sonra yaymak mı? P0 dersi: büyük
> patlama riskli. Önerim: kritik zincir önce.

---

## Verification Plan

- **E4 crash/recovery testi** (Testcontainers): commit sonrası incomplete publication →
  republish → exactly-once-effect.
- **Idempotency testi:** çift teslim → tek yan etki.
- **Tenant restore testi:** republish edilen event doğru tenant'ta (P0 sinerjisi).
- **Mevcut suite (720) yeşil kalmalı** — `@ApplicationModuleListener` geçişi davranışı bozmamalı.
- **ArchUnit (E5)** yeşil, build-kıran.

---

## Definition of Done

- [ ] E0: event'ler sınıflı (entegrasyon vs içsel), ADR'de.
- [ ] E1: dayanıklı set `@ApplicationModuleListener`; republish-on-restart açık;
      **aspect pointcut `@ApplicationModuleListener`'ı kapsıyor.**
- [ ] E2: `processed_event` dedup + handler sarmalayıcı; at-least-once güvenli.
- [ ] E3: republish tenant-context'i doğru restore ediyor (RLS altında).
- [ ] E4: crash/recovery + idempotency + tenant-restore testleri yeşil.
- [ ] E5: ArchUnit korkuluğu yeşil, build-kıran.
- [ ] Mevcut 720 test yeşil kalıyor.

---

## P0 ile Sinerji (neden şimdi kolay)

P0'da event'lerin `tenantId` taşımasını zorunlu kıldık (`DomainEvent` null-guard) ve
`TenantRestoringEventListenerAspect` ile async listener'larda context'i payload'dan restore
ettik. Dayanıklı republish **tam da** context'siz bir thread'de event'i yeniden çalıştırmak
demek — yani P0'ın çözdüğü problem. Bu faz, P0'ın üzerine doğal oturuyor; tek dikkat
edilecek nokta E1'deki pointcut tuzağı.
