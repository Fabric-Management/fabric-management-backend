🛡️ FABRIC AI GATEWAY & POLICY GUARD

Version: 1.0
Status: Design Approved — Ready for Rollout
Scope: AI trafiğinin güvenli geçidi + eylem koruması (RBAC/ABAC, risk yönetimi, onay akışları)

🎯 PURPOSE

AI isteklerini merkezi bir geçit üzerinden toplayıp:

Kimlik & Yetki (RBAC/ABAC) kontrolünü yapar,

Risk skorlar ve guardrail uygular,

Politika’ya göre tool/endpoint erişimi kısıtlar,

Yüksek etkili eylemlerde Confirm-or-Abort (insan onayı) süreci işletir,

Tüm akışı güvenli log/audit ile kayıt altına alır.

🧱 ARCHITECTURE (High-Level)
[Web / Mobile / Voice]
        │
   ┌──────────────┐
   │  AI Gateway  │  ← Token verify, rate limit, PII redaction, tenant isolation
   └─────┬────────┘
         │
   ┌──────────────┐
   │ Policy Guard │  ← RBAC/ABAC, Risk scoring, Tool allowlist, Confirm-or-Abort
   └─────┬────────┘
         │
 [fabric-intelligence-service]
         │           (intent → plan → tool calls)
   ┌─────┴─────┐
   │  Domain   │  ← Order/Task/Inventory/…
   │  Services │
   └───────────┘

🧩 COMPONENTS
Bileşen	Rol
Auth Filter	JWT/MTLS doğrulama, tenant sınırlandırma
Rate Limiter	Kanal-bazlı hız sınırı (user/device/tenant)
PII Redactor	Prompt/response’ta e-posta/telefon/IBAN maskeleme
Policy Resolver	Tenant→Department→User hiyerarşisiyle nihai politika
Risk Engine	Intent/Tool/Context’e göre LOW/MED/HIGH risk skoru
Guardrail Engine	Allowed tool/endpoint, output filter, prompt budget
Confirm Service	HIGH risk aksiyonlar için onay döngüsü
Audit Logger	Prompt, tool-calls, kararlar için güvenli log (redacted)
🔐 POLICY MODEL (Conceptual)
{
  "tenantId": "uuid",
  "channels": {
    "web": { "rate": "30/m", "burst": 60 },
    "voice": { "rate": "15/m", "burst": 30 },
    "api": { "rate": "60/m", "burst": 120 }
  },
  "rbac": {
    "roles": {
      "CUSTOMER": { "intents": ["check_stock","create_order_draft"], "tools": ["Orders.read","Inventory.read"] },
      "SALES":    { "intents": ["create_order","check_stock","send_quote"], "tools": ["Orders.write","Inventory.read","Notify.write"] },
      "PLANNING": { "intents": ["create_mo","assign_task"], "tools": ["Tasks.write","Orders.read"] },
      "MANAGER":  { "intents": ["approve_order","approve_price","assign_task"], "tools": ["Orders.approve","Pricing.approve","Tasks.write"] },
      "ADMIN":    { "intents": ["*"], "tools": ["*"] }
    }
  },
  "abac": {
    "constraints": [
      { "attr": "department", "equalsAny": ["SALES","PLANNING","WAREHOUSE"] },
      { "attr": "region", "equals": "${user.region}" }
    ]
  },
  "risk": {
    "rules": [
      { "intent": "approve_order", "risk": "HIGH" },
      { "intent": "create_order", "risk": "MED", "amountThreshold": 0 },
      { "intent": "send_email", "risk": "LOW" }
    ],
    "confirmOn": ["HIGH"],
    "blockOn": ["CRITICAL"]
  },
  "tools": {
    "Orders.write": {
      "allow": ["POST /orders","PATCH /orders/{id}"],
      "deny":  ["DELETE /orders/{id}"],
      "fieldPolicies": { "discount": { "max": 0.1 } }
    },
    "Pricing.approve": {
      "allow": ["POST /pricing/approve"],
      "confirm": true
    }
  },
  "outputFilters": {
    "maskPII": true,
    "forbid": ["password","apiKey","rawToken"]
  }
}

🧭 POLICY EVALUATION ORDER

Token doğrulama & tenant isolation

Rate limit & channel check

RBAC: rol bazlı intent/tool yetkisi

ABAC: departman/bölge/proje gibi özelliklerle daraltma

Risk Engine: niyet + miktar + müşteri/tutar/fiyat politikası → risk skoru

Guardrail: tool allowlist/denylist, alan bazlı sınırlamalar

Confirm-or-Abort: HIGH risk ise onay döngüsü

Audit: redacted prompt + karar + eylem kaydı

⚠️ RISK MATRIX
Risk	Örnek	Davranış
LOW	Stok sorgu, özet oluşturma	Direkt yürüt
MED	Sipariş taslağı oluşturma, düşük tutarlı eylemler	Yürüt + bildirim
HIGH	Sipariş onayı, fiyat onayı, sevkiyat başlatma	Confirm-or-Abort zorunlu
CRITICAL	Veri dışa aktarımı, geniş yetkili silme	Block + uyarı

Dinamik faktörler: tutar, müşteri risk skoru, indirim oranı, saat (mesai dışı), kullanıcı güven skoru.

✅ CONFIRM-OR-ABORT (Human-in-the-Loop)

Akış:

AI planı ve etki alanını özetler (kim, ne, ne zaman, ne değişecek).

Kullanıcıya net onay sorusu:

“Bu siparişi onaylayıp planlamaya göndereyim mi?”

Yanıt: Confirm → işlemi uygula; Abort → iptal et, not düş.

Örnek Onay Kartı (UI metni):

Eylem: Sipariş Onayı
Sipariş: #SO-8421 (Müşteri: X Tekstil)
Satırlar: 3 (toplam 1.240 m)
Fiyat: ₺4,83/m (floor üzeri)
Etkisi: Planning task açılacak, sevkiyat tahmini +2 gün
Onaylıyor musunuz? [Evet] [Hayır]

🚦 RATE LIMIT & QUOTAS

Kanal (web/voice/api) başına ayrı limit

Kullanıcı ve tenant başına kova (token bucket)

Patlama koruması (burst) ve soğuma süreleri

Policy override: Yönetici/servis hesapları için esnek limitler

Örnek: web: 30/m, voice: 15/m, api: 60/m (tenant politika ile değişir)

🧹 PII REDACTION & DATA MINIMIZATION

Prompt/response’taki PII alanları maskele (email, tel, IBAN, adres).

Sadece gerekli alanları AI modeline gönder (minimum context).

Loglarda hash + redaction uygula; tam PII hiçbir zaman saklanmaz.

Maskeyi policy flag ile zorunlu kıl (maskPII = true).

🔍 ALLOWED TOOLS MATRIX (Guardrail)

Tool/endpoint isimleri allowlist ile yönetilir.

Denylist kritik eylemleri bloklar (örn. DELETE).

Field policy ile sayısal alanlar sınırlandırılır (örn. discount ≤ 10%).

Cross-service tool calls için “max hop” limiti.

🧾 AUDIT & OBSERVABILITY

Redacted Prompt, Context kaynakları (RAG), Plan, Tool çağrıları (request/response özet), Risk skoru, Karar (confirm/abort), Latency.

Trace id: X-Correlation-Id ile domain hizmetlerine zincirlenir.

Anomali sinyali: sıra dışı çağrı paterni → security alert.

🧠 PROMPT GUARDRAILS

Context budget: max 6–8 snippet + her zaman canlı API snapshot.

Plan-then-Act zorunlu (önce planı yaz, sonra tool çağır).

Tool-only answers: hesap/stok/veri kümesi konularında model tahmini değil, tool cevabı.

Toxicity/PII filter: istenmeyen içerikleri bastır.

Language policy: Kullanıcı diline göre yanıt; jargon sadeleştir.

🔗 ENDPOINT (Gateway-facing)

POST /api/v1/intelligence/ask

Headers: Authorization, X-Tenant-Id, X-Request-Id, X-Channel

Body: { "utterance": "...", "context": {...}, "intentHint": "create_order" }

Behavior: Auth→Policy→Risk→Guardrail→(Confirm?)→Dispatch→Audit

Response: { "message": "...", "actions":[...], "confirm": true|false, "traceId": "..." }

POST /api/v1/intelligence/confirm

Onay kartı için Evet/Hayır cevabı

Body: { "traceId":"...", "decision":"CONFIRM|ABORT" }

Not: Domain çağrıları intelligence-service üzerinden yapılır; gateway direkt domain’e yazmaz.

🧪 DEFAULT POLICIES (Phase-0/1)

Phase 0 (Read-only Assist):

Tools: *.read, Notify.draft

Risk = LOW → serbest, MED/HIGH → blok

Amaç: güvenli başlamak, faydayı göstermek

Phase 1 (Low-risk Writes):

Tools: Tasks.write, Orders.draft, Notify.send (onayla)

Risk LOW/MED → serbest; HIGH → confirm

SLA ölçümleri + memnuniyet takibi

📊 METRICS (Gateway/Guard Level)
Metrik	Hedef
Policy deny oranı	≤ %1
Confirm gerektiren çağrı oranı	%5–%15 (kullanıma göre)
Confirm süresi P95	≤ 30s
Prompt redaction coverage	%100
Tool success rate	≥ %98
Avg. latency (gateway)	≤ 150ms
⚠️ FAILURE MODES & HANDLING

Policy mismatch: “Bu eylem yetki alanınızın dışında.” → öneri sun.

Risk compute fail: Varsayılan HIGH kabul et, confirm iste.

Rate limit: “Kısa süre sonra tekrar deneyin.” (Retry-After header)

Tool error: Özetle, güvenli fallback ver; kullanıcıya net hata mesajı.

✅ SUMMARY

AI Gateway & Policy Guard, AI güdümlü eylemleri güvenli, denetlenebilir ve politika uyumlu kılar:

Kimlik & yetki → RBAC/ABAC

Davranış → Policy-driven

Risk → Score + Confirm-or-Abort

Veri → PII redaction & tenant isolation

İzlenebilirlik → Audit trail & metrics

Sonuç: AI hızlı çalışır, ama asla kontrolsüz çalışmaz.