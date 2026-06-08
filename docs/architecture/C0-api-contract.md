# Faz: OpenAPI-First Kontrat — Backend↔Frontend Dikişi

> **Amaç:** OpenAPI spec'ini **tek doğruluk kaynağı** yapmak; backend değişince spec değişsin,
> spec değişince frontend tipli client'ı yeniden üretilsin ve **compile'da** kırılsın — runtime'da
> değil. İki taraf tek organizma gibi evrilsin. Senin "backend + frontend + UI/UX bir bütün"
> hedefinin dikiş yeri.
>
> **Kalibrasyon:** Bu, "değiştirmesi pahalı, şimdi öde" listesindeki son temel. Ama 93
> controller'a elle **spec-first** retrofit pahalı ve hataya açık. Onun yerine **code-first +
> kontrat kapısı**: springdoc spec'i üretir, biz onu commit'lenen bir snapshot + CI drift-gate
> ile gerçek bir kontrata çeviririz. Faydanın %90'ı, maliyetin %20'si.

---

## Mevcut Durum (Kod Araştırması)

| Bileşen | Durum |
|---------|-------|
| `springdoc-openapi-starter-webmvc-ui` 2.6.0 | ✅ var → runtime `/v3/api-docs` + Swagger UI |
| `OpenApiConfig.java` | ✅ var (temel config) |
| `@RestController` | 93 dosya |
| `@Tag` / `@Operation` zengin annotation | ⚠️ ~33-34 dosya (≈60 controller **çıplak**) |
| `@Schema` (DTO) | ~50 dosya |
| DTO'lar | ✅ `record` (CLAUDE.md) → schema üretimi temiz |
| **Hata modeli** | 🔴 **parçalı:** `ApiError` + `GlobalExceptionHandler` **+** modül-bazlı ayrı handler'lar (StockUnit, Approval, …) → tutarsız error shape |
| Commit'lenen spec dosyası | ❌ yok → spec sadece runtime'da, versiyonlanmıyor, review edilemiyor |
| Frontend client gen | ❌ yok (frontend ayrı repo) |
| Drift gate (CI) | ❌ yok → API değişikliği görünmez/gözden kaçabilir |

**Çıkarım:** Üretim altyapısı (springdoc) var ama spec **kararsız** (versiyonlanmıyor),
**düzensiz** (yarısı annotate değil) ve **tutarsız** (parçalı error modeli). Kontrat olması için:
tutarlı error şeması + operationId/schema disiplini + commit'lenen snapshot + CI drift-gate +
frontend client gen setup'ı.

---

## Tasarım Kararı

| # | Konu | Karar |
|---|------|-------|
| 1 | **Kontrat modeli** | **Code-first + kontrat kapısı** (spec-first retrofit DEĞİL). springdoc üretir → repo'ya snapshot → CI drift-gate. Mevcut 93 controller'a uygun, düşük maliyet |
| 2 | **Hata şeması** | **RFC 7807 `ProblemDetail`** (Spring Boot 3 native) — tek tip; parçalı handler'lar tek modele konsolide. Frontend tek error tipi alır |
| 3 | **Client gen** | Frontend repo'sunda commit'lenen spec'ten üretilir (`openapi-typescript` veya `openapi-generator` typescript-fetch). Backend bu repo'da spec + setup'ı sağlar |
| 4 | **Spec konumu** | `api/openapi.yaml` (commit'li, versiyonlu) — build'de export edilir, CI üretilenle karşılaştırır |
| 5 | **operationId/schema disiplini** | Stable operationId (client metod adı olur) + anlamlı schema adları zorunlu — yoksa client çirkin/kararsız |

---

## Görevler

### C0 — Kontrat ADR
Code-first + drift-gate kararı, RFC7807 error modeli, versiyonlama politikası (`/api/v1`,
breaking-change kuralı), client-gen yaklaşımı. ADR'ye yaz.

### C1 — Tutarlı hata modeli (🔴 en yüksek değer)
- Parçalı handler'ları (`StockUnitExceptionHandler`, `ApprovalExceptionHandler`, modül
  exception'ları) **tek** bir `@RestControllerAdvice` + **RFC 7807 `ProblemDetail`** modeline konsolide et.
- Her hata yanıtı aynı şema: `type`, `title`, `status`, `detail`, `instance`, + domain `code`
  (ör. `PLATFORM_ROLES_NOT_INITIALIZED`) ve validation için `errors[]`.
- Spec'te tek `ProblemDetail` schema'sı; tüm endpoint'ler 4xx/5xx için onu referanslar.
- **Neden önce bu:** tutarsız error'lı kontrat zayıf kontrattır; frontend tek error tipiyle çalışmalı.

### C2 — Annotation disiplini (çıplak ~60 controller)
- Her endpoint: stable `operationId` (camelCase, benzersiz), `@Tag` (modül), response kodları
  (`@ApiResponse` 200/201/4xx/5xx), request/response `@Schema`.
- Çıplak controller'ları minimum-yeterli annotate et — gold-plating yok, ama operationId + tag + ana response'lar şart.
- DTO record'ları zaten schema üretiyor; eksik `@Schema(description=...)` ekle.

### C3 — Spec export + versiyonlama
- Build'de `/v3/api-docs` çıktısını `api/openapi.yaml`'a export et (springdoc Maven plugin veya
  bir IT ile yaz).
- `api/openapi.yaml` commit'lenir → spec artık review-edilebilir, diff'lenebilir, versiyonlu.

### C4 — CI drift-gate (kontrat kapısı)
- CI'da: spec'i yeniden üret, commit'li `api/openapi.yaml` ile karşılaştır → **fark varsa build kırılır.**
- Böylece her API değişikliği snapshot güncellemesini zorunlu kılar → değişiklik görünür + review'dan geçer.
- Bu, code-first'ü gerçek bir kontrata çeviren mekanizma.

### C5 — Frontend client gen setup'ı
- Commit'li spec'ten tipli TS client üreten komut/config (`openapi-typescript` veya
  `openapi-generator-cli typescript-fetch`) — frontend repo'sunda koşar.
- Dokümante et: "backend değişir → spec değişir (C4 gate) → frontend client regen → TS compile kırılır."
- (Frontend ayrı repo olduğu için burada setup + dokümantasyon; gerçek gen orada.)

### C6 — Korkuluk
- ArchUnit/test: her `@RestController` endpoint'inin `operationId`'si var + spec'te görünüyor.
- Test: tüm hata yanıtları `ProblemDetail` şemasına uyuyor (parçalı shape kalmadı).
- C4 drift-gate zaten ana korkuluk.

---

## Open Questions

> [!IMPORTANT]
> **Q1 — Code-first + gate vs spec-first:** 
> **Karar:** Code-first + drift-gate yaklaşımı onaylandı. Mevcut 93 controller için en pragmatik ve maliyet-etkin çözümdür.

> [!IMPORTANT]
> **Q2 — Error modeli geçişi kapsamı:** 
> **Karar:** Tüm modüller için tek seferde geçiş. Tek `@RestControllerAdvice` kurulacak, modül bazlı handler'lar (StockUnit, Approval) emekli edilecek ve `ProblemDetail`'e standartlaşılacak.

> [!NOTE]
> **Q3 — Versiyonlama:** 
> **Karar:** Evet, tüm path'ler `/api/v1/...` şekline taşınacak. Gelecekte breaking-change yönetimi için v2 hazır edilmiş olacak.

> [!NOTE]
> **Q4 — Client gen aracı:**
> **Karar:** React/Next.js (TS) stack varsayımıyla `openapi-typescript` + `openapi-fetch` kullanılacak. `openapi-generator`'ın şişkinliğinden kaçınılarak native fetch tabanlı, hafif ve tip güvenli client üretilecek.

---

## Verification Plan

- **Drift-gate testi:** controller'a bir alan ekle → spec yeniden üretilince fark → CI kırılır (kanıt).
- **Error tutarlılığı:** farklı modüllerden hata fırlat → hepsi `ProblemDetail` şemasında döner (IT).
- **operationId korkuluğu:** operationId'siz endpoint → test kırılır.
- **Spec geçerliliği:** `api/openapi.yaml` valid OpenAPI 3 (lint).
- **Mevcut suite (720+) yeşil kalmalı.**

---

## Definition of Done

- [ ] C0: kontrat ADR (code-first+gate, RFC7807, versiyonlama, client-gen).
- [ ] C1: tek `@RestControllerAdvice` + `ProblemDetail`; parçalı handler'lar emekli; tutarlı error şeması.
- [ ] C2: tüm endpoint'lerde stable operationId + tag + ana response'lar + schema.
- [ ] C3: `api/openapi.yaml` export edilip commit'lendi (versiyonlu).
- [ ] C4: CI drift-gate — spec değişikliği build'i kırıyor (snapshot güncellemesi zorunlu).
- [ ] C5: frontend client-gen komutu/config + "compile-break" döngüsü dokümante.
- [ ] C6: operationId + error-shape korkulukları yeşil.
- [ ] Mevcut 720+ test yeşil.

---

## P0 + E0 + Observability ile İlişki

Bu faz, altyapı temellerinin **dışa açılan yüzü.** Üç temel (izolasyon, dayanıklı event,
observability) sistemin **içini** sağlamlaştırdı; kontrat, **dışını** (frontend/istemci) tek
organizmaya bağlar. Bundan sonra feature geliştirme — backend + frontend — kontrat zemininde
güvenli ve hızlı ilerler: bir uçtaki değişiklik diğer uçta compile-time'da yakalanır.
