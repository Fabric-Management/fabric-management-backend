🤖 FABRIC AI CAPABILITY MATRIX

Version: 1.0
Status: Design Approved — Ready for Phased Rollout
Scope: Module-by-Module AI Integration Map (Private-first, Policy-driven)

🧭 Principles (TL;DR)

Private-first, Cloud-smart: Yerel LLM (Ollama/Mistral/Gemma/Phi) → gerekirse bulut fallback

Policy-driven: Kim hangi AI fonksiyonunu kullanabilir, veri sınıflandırması (Public/Restricted)

RAG by default: Her akışta kurumsal hafıza/vektör arama + canlı API bağlamı

Agentic pattern: Intent → Plan → Act → Verify → Log (Toolformer yaklaşımı)

Observability: Prompt/response, tool calls, başarı oranı, kullanıcı memnuniyeti metriklenir

🧩 Capability Grid (by Service)
Service	AI Capabilities	Primary Intents / Tools	Local Models (default)	Cloud Fallback (optional)	Data Sources (RAG)	KPIs
fabric-intelligence-service	Intent detection, agent orchestration, response synthesis, tool-use planning	create_order, check_stock, assign_task, approve_order, send_email	Llama3/Mistral (Ollama), Whisper (STT), Coqui/Bark (TTS)	GPT-5 / Claude 3.5 (kompleks planlama)	Vector store (KB), Policies, User/Role, Context Graph	Intent accuracy ≥95%, action success ≥98%, latency ≤1.5s
fabric-order-service	Conversational order creation, policy validation, anomaly hints, summary emails	draft_order, validate_terms, explain_pricing, timeline_summary	Mistral-Instruct	GPT-5 mini	Order index, Pricing rules, Product catalog	Draft→Approved conversion rate, correction rate↓
fabric-task-service	Auto-prioritization, SLA breach prediction, escalation assistant, summarization	prioritize_pool, predict_breach, route_to_queue, daily_digest	Gemma/Phi (fast)	GPT-5	Task store, SLA policy	SLA compliance↑, overdue↓, mean time to close↓
fabric-inventory-service	Stock Q&A, depletion forecasting, substitution suggestions	check_stock, forecast_depletion, suggest_substitute	Mistral small	GPT-5	Inventory snapshots, Lead times, Substitution map	Stock-out rate↓, forecast MAPE↓
fabric-pricing-service	Price recommendation, floor check explanation, negotiation helper (assistive)	recommend_price, explain_floor, simulate_discount	Gemma-Instruct	GPT-5	Costing outputs, Price lists, Customer tier	Margin↑, discount leakage↓
fabric-costing-service	Cost driver explanation, what-if simulation (assistive), anomaly detection	explain_cost, simulate_fx, detect_outlier	Llama3	GPT-5	BOM/recipe, FX history, Energy/Labor tables	Cost variance↓, what-if cycle time↓
fabric-fiber-service	Knowledge Q&A (properties), mapping assist for blends	explain_fiber, validate_blend	Gemma	—	Fiber catalog (vector)	First-pass validation accuracy↑
fabric-yarn/weaving/finishing	Spec Q&A, routing hints, production planning notes (assistive)	suggest_route, estimate_runtime, quality_hint	Mistral small	—	Recipes, KPIs, machine history	Plan adherence↑
fabric-catalog-service	Semantic product search, alternative suggestions	semantic_search, suggest_alternative	E5/SBERT + Mistral	—	Catalog vectors	CTR↑, time-to-product↓
fabric-notification-service	Tone-aware message drafting, multi-channel summarization	draft_email, draft_whatsapp, summarize_order	Gemma/Phi	—	Templates, events	Reply rate↑
fabric-company/contact-service	Duplicate detection (fuzzy), enrichment suggestions	detect_duplicate, suggest_enrichment	SBERT + rules	—	Company index, external notes	Duplicate rate↓
fabric-analytics-service	NL-to-Insight, KPI narrative, anomaly storytelling	ask_insight, explain_anomaly	Llama3	GPT-5	Metrics warehouse	Insight adoption↑
🧠 Agent Roles & Boundaries
Agent	Scope	Tools it can call	Guardrails
SalesAgent	Sipariş, stok, fiyat öneri	Orders API, Inventory API, Pricing explain	Customer PII masked, no delete ops
ProductionAgent	Üretim task/plan	Task API, Order lines, Capacity read	Only suggest plan; create MO via approval policy
WarehouseAgent	Hazırlık/Sevkiyat	Task API, Inventory reserve	No shipment without policy OK
ManagerAgent	Onay/özet/eskalasyon	Orders/Tasks/Analytics read, Approval cmd	Enforce RBAC/ABAC; high-risk ops require confirm
SupplierAgent	PO/teyit	Procurement API, Notification	Never shares tenant secrets
📦 Context & RAG Blueprint

Vector Store (per tenant): Milvus/FAISS → embeddings: text-embedding-3-large eşleniği (lokal alternatifi: BGE/E5)

Context Graph: User↔Role↔Tenant↔Entity (Order/Task/Company/Product)

Retrievers: hybrid (sparse + dense) → top-k + re-rank (Cohere rerank local eşleniği: bge-reranker)

Fresh Data: Always-on tool use (live API calls) → no stale answers

Prompt Template (core):

System: role, safety, tenant scope

Tools: allowed endpoints, schemas, constraints

Context: top-k docs + live snapshots

User: utterance

Policy: RBAC + risk level

Output: plan → tool calls → user message

🔒 Safety & Governance

RBAC + ABAC + Policy gating (risk levels: LOW/MED/HIGH)

Data classification: Public/Restricted/Secret → AI only accesses allowed scopes

PII masking: Logs & prompts’ta e-posta/telefon/IBAN maskelenir

Action confirmation: High-impact (e.g., send PO, approve order) → “confirm step”

Secure logging: Prompt, tool calls, outputs → hash + redact → audit trail

Feedback loop: 👍/👎, reason tags (hallucination/latency/bad action)

📊 Metrics & Evaluation
Dimension	Metric	Target
Accuracy	Intent F1 (macro), Tool call success	≥95%, ≥98%
Utility	Task throughput uplift, time saved per flow	+25% throughput
Quality	User CSAT (1–5), Hallucination rate	≥4.5, ≤0.5%
Speed	P50/P95 latency	≤1.5s / ≤3s
Safety	Policy violations per 1k	≤0.2

Offline Eval: synthetic task suites (orders, tasks, inventory) + golden answers
Online Eval: A/B with guardrail, progressive rollout (10%→50%→100%)

🚀 Rollout Plan (Phased)

Phase 0 – Read-Only Assist (2–3 hafta)

NL Q&A (stok, sipariş durumu, basit öneriler)

No write ops, only suggestions + summaries

Phase 1 – Low-risk Actions (3–4 hafta)

Task create/assign, order draft, email draft

Human-in-the-loop confirmation

Phase 2 – Policy-gated Actions (4–6 hafta)

Auto-approve (policy-enabled), shipment scheduling, PO draft

Manager confirmation for high-impact

Phase 3 – Autonomous Flows (opsiyonel)

Recurrent tasks, proactive alerts, auto-escalation

Strict guardrails + continuous monitoring

🛠️ Runtime Architecture (High-Level)
[User/Channel: Web, Mobile, Voice]
            │
      [AI Gateway]
            │
 [fabric-intelligence-service]
   ├─ Intent Engine
   ├─ Context Builder (Tenant/RBAC)
   ├─ RAG Layer (Vector + Live APIs)
   ├─ Agent Planner (Toolformer)
   ├─ Policy Guard (ABAC/Risk)
   └─ Response Synthesizer
            │
   ┌────────┴────────┐
   │                 │
[Domain APIs]   [Notification]
(Order/Task/Inv)   (Mail/SMS/WA)

🧩 Model Catalog (Private-first)
Task	Local Preferred	Notes
General reasoning	Mistral-Instruct / Llama3-Instruct	Ollama deploy
Fast classification	Phi-3 / Gemma-2B	Low-latency
Embeddings	BGE-Large / E5-Large	Multilingual strong
Reranking	bge-reranker-base	Improves retrieval quality
STT	Whisper small/medium	On-prem
TTS	Coqui-XTTS	Multilingual

Cloud fallback yalnızca “complex planning” ve “long context” için.

🧰 Prompt & Tooling Patterns

Plan-then-Act: “Planı yaz → hangi tool’ları çağıracağını sırala → sırayla çağır → sonucu özetle.”

Chain-of-Verification: Tool dönüşlerini doğrulayan kural (ör. stok sayısı < 0 olamaz → yeniden sorgula)

Context Budgeting: Max 6–8 snippet + zorunlu canlı API snapshot

Persona conditioning: Agent = role-aware (SalesAgent/ManagerAgent)

Confirm-or-Abort: Riskli eylemler için net onay sorusu; no silent action

⚠️ Risks & Mitigations
Risk	Mitigation
Hallucination	Strict RAG + tool-only answers + verification rules
Data leak	Tenant-isolated vector DB + PII redaction + scoped tokens
Over-automation	Human-in-loop checkpoints + Policy gating
Latency	Local models + caching + async tool calls
Drift	Feedback loop, weekly eval sets, policy updates
✅ Summary

Bu matris, hangi serviste hangi AI yeteneğini devreye alacağımızı,
hangi model ve veri kaynakları ile çalışacağımızı ve başarıyı nasıl ölçeceğimizi netleştirir.

Strateji: Private-first, Policy-driven, RAG-always, Agentic.

Uygulama: Phase 0→3 kademeli yayılım, her adımda guardrail + ölçüm.