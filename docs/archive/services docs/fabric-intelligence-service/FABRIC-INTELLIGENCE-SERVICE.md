🧠 FABRIC-INTELLIGENCE-SERVICE

Domain Architecture & Specification (AI-Native Design)
Version: 1.0
Status: 🧬 DNA-COMPLIANT
Base Path: /api/v1/intelligence
Port: 8110

🎯 PURPOSE

fabric-intelligence-service, Fabric Management Platform’un dijital zekâ katmanıdır.
Tüm kullanıcı, görev, sipariş ve üretim süreçlerine doğal dil, karar desteği ve otomasyon kabiliyeti kazandırır.

Servis, kullanıcıların konuşarak veya yazarak sistemle etkileşime geçmesini sağlar;
niyetleri algılar, uygun mikroservislerle iletişim kurar, işlemleri başlatır veya önerilerde bulunur.

💬 “AI artık sadece destek değil, operasyonun bir parçası.”

🧱 DOMAIN BOUNDARY

In-Scope:

Niyet tanıma (Intent Detection & Context Understanding)

Agent tabanlı diyalog yönetimi

Task, Order, Inventory, Notification entegrasyonları

AI destekli karar motoru (Recommendation & Prediction)

Text, Voice, Vision destekli çoklu modalite

Policy tabanlı davranış yönetimi (role-based AI limits)

Out-of-Scope:

Model eğitimi (LLM fine-tuning)

Fiziksel üretim hattı sensör yönetimi (Edge AI ayrı domain)

🧩 CORE COMPONENTS
Component	Description
Intent Engine	Kullanıcının ne yapmak istediğini anlar (create_order, check_stock, assign_task, approve_order, vb.)
Context Graph	Kullanıcının kimliği, tenant bilgisi, rolü, aktif ekranı ve işlem geçmişini analiz eder
Action Orchestrator	Uygun mikroservise doğru API çağrısını üretir ve yönetir
Response Synthesizer	Yanıtları doğal dil, ses veya mesaj formatında döner
Agent Framework	Her domain için özelleşmiş yapay zekâ ajanlarını yönetir
Knowledge Base Connector	Belgeler, dökümanlar ve sistem içi hafıza sorgularını yönetir
Policy Layer	AI’nin davranış sınırlarını (kim neyi yapabilir) belirler
Local Model Host	Yerel (offline) LLM modellerini çalıştırır (Ollama, Mistral, Gemma, Phi)
Fallback Cloud Layer	Gerektiğinde OpenAI, Anthropic, Gemini gibi harici modelleri devreye alır
🧠 INTENT CATEGORIES
Category	Examples
Order Operations	“Şu müşteri için yeni sipariş oluştur”, “Üretim emri başlat”, “Satınalma formu hazırla”
Task Management	“Bu task’ı Pınar Hanım’a ata”, “Açık görevleri sırala”, “Bu sipariş için task oluştur”
Inventory & Product	“Gabardin kumaş stokunu kontrol et”, “Renk 42900 açık bej stokta var mı?”
Analytics & Reporting	“Bu hafta kaç sipariş onaylandı?”, “Planlama departmanının SLA oranı nedir?”
Communication	“Bu siparişi maille gönder”, “Ahmet Bey’e WhatsApp mesajı at”
Managerial Actions	“Üretim hattı A’nın performans raporunu getir”, “Satış tahminini güncelle”
🧩 AGENT TYPES
Agent	Purpose	Example Interaction
🧍‍♂️ SalesAgent	Müşterilerle ve satış temsilcileriyle konuşarak sipariş oluşturur	“Ahmet Bey, bu kumaştan stokta 520 metre mevcut”
🏭 ProductionAgent	Planlamacıya yardımcı olur, üretim emirlerini yönetir	“Yeni üretim task’ı oluşturayım mı?”
📦 WarehouseAgent	Depo süreçlerini takip eder, sevkiyat durumunu bildirir	“Bu parti sevkiyata hazırlandı, task’ı güncelliyorum.”
💼 ManagerAgent	Yöneticilere özet, analiz ve öneri sunar	“Bu hafta 28 sipariş kapatıldı, %9 artış var.”
🤝 SupplierAgent	Tedarikçilerle etkileşim kurar, satınalma sürecini yönetir	“X firmasıyla teklif süreci başlatılsın mı?”
⚙️ WORKFLOW EXAMPLES
🗣️ Voice/Chat Order Creation
User: “Hey AI, 20/1 Gabardin kumaştan 1000 metre sipariş geçelim.”
AI: “Hemen kontrol ediyorum... Stokta 700 metre var. Eksik kalan için üretim mi yapılsın?”
User: “Evet, üretim emri oluştur ve A Dokumasına mail gönder.”
AI: “Sipariş ve üretim emri oluşturuldu, onay maili gönderildi ✅”

🧾 Manager-Driven Task Automation
Manager: “AI, bu siparişi planlamaya gönder ve Pınar Hanım’a ata.”
AI: “Tamam, task oluşturuldu. Pınar Hanım bilgilendirildi.”

📈 Analytics Conversation
User: “Bu ay planlama performansımız nasıl?”
AI: “Planlama departmanı 42 görev tamamladı. SLA uyumu %91, geçen aya göre +4% artış.”

🔗 INTEGRATION MAP
Connected Service	Purpose
fabric-task-service	Task oluşturma, atama ve güncelleme işlemleri
fabric-order-service	Sipariş oluşturma, onaylama, iptal etme
fabric-inventory-service	Stok kontrol ve üretim önerileri
fabric-notification-service	E-posta, SMS, WhatsApp veya in-app mesaj gönderimi
fabric-company-service	Müşteri ve tedarikçi bilgilerini getirme
fabric-analytics-service	KPI ve performans verilerini alma
fabric-user-service	Kullanıcı kimliği, rolü, dil ve yetki bilgisi
fabric-catalog-service	Ürün ve varyant özelliklerini sorgulama
🧠 INTELLIGENCE CAPABILITIES
Capability	Description	Example
Predictive Analytics	Stok, satış, üretim tahminleri	“Bu kumaş 3 günde tükenecek.”
Decision Support	Karar önerisi üretir	“Bu sipariş yerel depodan gönderilirse 1 gün kazanılır.”
Conversational Automation	Doğal dil ile işlem yürütür	“Bu task’ı tamamla ve planlamaya aktar.”
Anomaly Detection	Hatalı sipariş, stok farkı, SLA ihlali tespiti	“Bu sipariş miktarı ortalamanın 5 katı.”
Knowledge Retrieval	Kurumsal bilgi tabanından yanıt üretir	“Gabardin kumaşın iplik oranı nedir?”
Summarization	Raporları, task’ları, mesajları özetler	“Son 10 siparişin durumu: 8 onaylandı, 2 beklemede.”
🧩 LOCAL & CLOUD MODEL STRATEGY
Type	Purpose	Example Models
Local (Private)	Offline, gizlilik öncelikli senaryolar	Ollama (Llama 3, Mistral, Gemma, Phi)
Hybrid (Optional)	Gelişmiş reasoning veya code generation	OpenAI GPT-5, Claude 3.5, Gemini 1.5
Voice Layer	Sesli etkileşim	Whisper (STT) + Bark / Coqui TTS
Vector Memory	Kurumsal arama ve hafıza	FAISS / Milvus + LangChain retrievers

💡 Design Motto:
Private-first, Cloud-smart.

“Model önce lokalde denenir, yetmezse bulut devreye girer.”

🔒 SECURITY & POLICY
Policy	Description
Role-based Intelligence Access	Hangi kullanıcı hangi AI fonksiyonlarını kullanabilir
Data Classification Awareness	AI yalnızca “public” veya “restricted” veriye erişebilir
Tenant Isolation	Her tenant için ayrı vektör hafıza (no data leak)
Audit Trail	Tüm AI aksiyonları kayıt altına alınır (prompt, output, timestamp)
Feedback Loop	Kullanıcı beğeni/dislike verisiyle model performansı izlenir
🧬 ARCHITECTURE OVERVIEW
fabric-intelligence-service
│
├── intent/
│   ├── IntentParser.java
│   └── IntentClassifier.java
│
├── context/
│   ├── ContextGraph.java
│   └── TenantContextResolver.java
│
├── orchestrator/
│   ├── ActionOrchestrator.java
│   └── ServiceConnector.java
│
├── agent/
│   ├── SalesAgent.java
│   ├── ProductionAgent.java
│   ├── WarehouseAgent.java
│   └── ManagerAgent.java
│
├── policy/
│   ├── AIPolicyEngine.java
│   └── RoleConstraintConfig.java
│
├── integration/
│   ├── NotificationClient.java
│   ├── TaskClient.java
│   └── OrderClient.java
│
└── resources/
    └── ai-models/
        ├── mistral.gguf
        ├── llama3.gguf
        └── vectorstore/

⚙️ EVENTS & TRIGGERS
Event	Source	Target	Purpose
ai.intent.detected	User	Intelligence Service	Niyet algılandı
ai.action.executed	Intelligence Service	Domain Service	İşlem başlatıldı
ai.decision.recommended	Intelligence Service	User Interface	Karar önerisi üretildi
ai.error.feedback	Domain Service	Intelligence Service	Hatalı yanıt öğrenimi
ai.memory.updated	Knowledge Base	Vector Store	Kurumsal hafıza güncellendi
🧮 PERFORMANCE METRICS
Metric	Description	Target
Intent Accuracy	Doğru niyet algılama oranı	≥ 95%
Action Success Rate	Başlatılan işlemlerin başarı oranı	≥ 98%
Response Latency	Ortalama cevap süresi	≤ 1.5s
User Satisfaction	Kullanıcı feedback skoru	≥ 4.5 / 5
🧩 FUTURE EXTENSIONS

Visual QA: kamera görüntüsünden kumaş kalite analizi

Predictive Production: üretim darboğazı tahmini

Auto-Supplier Match: tedarikçilerin otomatik seçimi

AI Workflow Designer: task & process akışlarını AI tasarlayabilir

Autonomous Agent Mode: tamamen insan girişi olmadan operasyon

🧠 DESIGN PRINCIPLES
Principle	Implementation	Status
ZERO HARDCODED VALUES	Tüm davranışlar policy/config bazlı	✅
AI-NATIVE ARCHITECTURE	Servis, her katmanda zekâ içerir	✅
CONTEXT-AWARE AUTOMATION	Her işlem context bazlı yapılır	✅
ORCHESTRATION + CHOREOGRAPHY	Hem yönlendirici hem reaktif davranış	✅
CLEAN CODE / SRP	AI bileşenleri modüler ve test edilebilir	✅
EVENT-FIRST	Tüm eylemler event olarak publish edilir	✅
PRIVATE-FIRST STRATEGY	LLM’ler öncelikle lokalde çalışır	✅

Compliance Score: 99/100 🌟

🧾 SUMMARY

Fabric Intelligence Service,
Fabric Management ekosisteminin düşünen, konuşan ve karar veren beynidir.
Her kullanıcı, her süreç ve her task artık konuşarak yürütülebilir.
AI yalnızca yanıt üretmez — işi yürütür, süreçleri bağlar ve kararları hızlandırır.

“From reactive ERP → to proactive digital enterprise.”

Document Owner: Fabric Management Architecture Team
Last Updated: 2025-10-21
Status: Logical Architecture Approved — Ready for Implementation