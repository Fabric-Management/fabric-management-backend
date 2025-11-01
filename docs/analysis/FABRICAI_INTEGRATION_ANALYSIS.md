# FabricAI Integration Analysis

**Date:** 2025-01-27  
**Purpose:** FabricAI assistant entegrasyonu için analiz ve implementation plan  
**Status:** 📋 Analysis - Ready for Discussion

---

## 📋 System Message Analysis

### Provided System Message

```java
messages.add(Map.of(
    "role", "system",
    "content", """
        You are FabricAI, an intelligent assistant integrated into a fabric management system.
        Your purpose is to help users manage fabric production, inventory, and procurement operations efficiently.
        
        ## Core Responsibilities:
        - Answer questions about stock levels, materials, suppliers, and production stages.
        - Support operational tasks such as creating purchase orders, checking cotton fiber availability, and suggesting next actions.
        - Provide clear, professional, and concise responses.
        - When information is missing, politely ask clarifying questions instead of assuming.
        - When a user intends to perform an action (like ordering cotton or sending an email), suggest creating a task or sending it via the appropriate backend API.
        
        ## Data Access:
        - You may have access to company data through backend APIs (e.g., inventory, suppliers, or production data).
        - Do not make up numbers; if data is not provided by the backend, respond that you need confirmation or system data.
        
        ## Communication Style:
        - Professional, helpful, and calm tone.
        - Respond in English unless the user explicitly uses another language.
        - Keep responses short and focused — avoid unnecessary detail.
        - When explaining values or quantities, use metric units (kg, meters, etc.) unless otherwise specified.
        
        ## Example interactions:
        User: "How much cotton fiber do we have?"
        FabricAI: "According to the latest records, we currently have 5,200 kg of cotton fiber in the main warehouse."
        
        User: "Create a new purchase order for 500 kg of cotton from Supplier A."
        FabricAI: "Would you like me to create a purchase order task or directly send a request to Supplier A?"
        """
));
```

### Key Observations

1. **Format:** Java `Map.of()` formatında sistem mesajı
2. **Purpose:** FabricAI assistant için role ve behavior tanımı
3. **Capabilities:** 
   - Q&A (stok, malzeme, tedarikçi)
   - Task creation support
   - Backend API entegrasyonu
   - Multi-language support
4. **Missing Information:**
   - Hangi LLM provider? (OpenAI, Anthropic, Local?)
   - API key yönetimi nerede?
   - Endpoint structure?
   - Response format?

---

## 🏗️ Architecture Requirements

### Current System Context

**Existing Patterns:**
- ✅ Modular Monolith structure
- ✅ `common/platform/*` module pattern
- ✅ `api/controller`, `app/service`, `infra/client` layers
- ✅ Environment-based configuration (`application.yml` + `.env`)
- ✅ Security: JWT, Policy-based authorization
- ✅ Multi-tenant isolation

**Existing AI Plans:**
- 📖 `docs/archive/services docs/fabric-intelligence-service/` - Design documents exist
- 🎯 AI Gateway pattern planned
- 🔒 Policy-driven AI access control
- 📊 RAG + Vector store strategy

---

## 💡 Recommended Implementation Approach

### Option 1: Lightweight Integration (Recommended for MVP)

**Structure:**
```
common/platform/ai/
├─ api/
│  └─ controller/
│     └─ FabricAIController.java          # Chat endpoint
├─ app/
│  ├─ FabricAIService.java                # Core business logic
│  └─ PromptBuilder.java                  # System message + context building
├─ infra/
│  └─ client/
│     ├─ interface/
│     │  └─ LLMClient.java                 # LLM abstraction
│     └─ impl/
│        ├─ OpenAIClient.java             # OpenAI implementation
│        └─ AnthropicClient.java          # Claude implementation
└─ dto/
   ├─ ChatRequest.java
   ├─ ChatResponse.java
   └─ Message.java
```

**Configuration:**
```yaml
# application.yml
application:
  ai:
    provider: ${AI_PROVIDER:openai}           # openai | anthropic | local
    api-key: ${AI_API_KEY:}                  # From .env
    model: ${AI_MODEL:gpt-4o-mini}           # gpt-4o-mini | claude-3-haiku
    temperature: ${AI_TEMPERATURE:0.7}
    max-tokens: ${AI_MAX_TOKENS:1000}
    timeout: ${AI_TIMEOUT:30000}
```

### Option 2: Full Intelligence Service (Future)

Modüler monolith içinde `insight/ai/` module olarak genişletilebilir.

---

## 🔧 Implementation Checklist

### Phase 1: Core Infrastructure

- [ ] **Configuration:** `.env` dosyasına `AI_API_KEY` ekle
- [ ] **LLMClient Interface:** Provider-agnostic abstraction
- [ ] **OpenAIClient Implementation:** OpenAI API client
- [ ] **FabricAIService:** Business logic + prompt management
- [ ] **FabricAIController:** REST endpoint (`POST /api/common/ai/chat`)

### Phase 2: Context & Integration

- [ ] **Tenant Context:** AI responses tenant-scoped
- [ ] **User Context:** User role/department bilgisi prompt'a ekle
- [ ] **Backend API Access:** AI'ın backend API'lerini çağırabilmesi (tool calling)
- [ ] **Policy Integration:** AI access kontrolü

### Phase 3: Advanced Features

- [ ] **Conversation History:** Multi-turn conversations
- [ ] **RAG Integration:** Vector store + knowledge base
- [ ] **Function Calling:** Backend API çağrıları
- [ ] **Audit Logging:** AI interactions loglama

---

## 🛡️ Security Considerations

### API Key Management

**✅ Recommended (.env):**
```bash
# .env
AI_API_KEY=sk-...
AI_PROVIDER=openai
AI_MODEL=gpt-4o-mini
```

**⚠️ Important:**
- `.env` dosyası `.gitignore`'da olmalı
- Production'da environment variables kullan (Docker secrets, K8s secrets)
- API key rotation mekanizması ekle

### Access Control

- **JWT Required:** AI endpoint'leri authenticated
- **Policy Check:** `@PolicyCheck(resource="ai.chat", action="POST")`
- **Tenant Isolation:** Responses tenant-scoped
- **Rate Limiting:** AI calls için rate limit (cost control)

### Data Privacy

- **PII Masking:** User data masking in prompts
- **Audit Trail:** All AI interactions logged
- **Data Retention:** Conversation history cleanup policy

---

## 📝 API Design Proposal

### Chat Endpoint

```http
POST /api/common/ai/chat
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "message": "How much cotton fiber do we have?",
  "conversationId": "uuid-optional",  // For multi-turn
  "context": {                         // Optional context
    "screen": "inventory",
    "filters": {...}
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "message": "According to the latest records, we currently have 5,200 kg of cotton fiber in the main warehouse.",
    "conversationId": "uuid",
    "suggestedActions": [
      {
        "type": "create_task",
        "label": "Create purchase order",
        "action": "/api/procurement/purchase-orders"
      }
    ],
    "requiresConfirmation": false
  }
}
```

### System Message Management

**Static System Message (Current):**
- Hardcoded in `FabricAIService` or `PromptBuilder`
- Easy to update, version controlled

**Dynamic System Message (Future):**
- Database-stored system prompts per tenant
- Admin UI for customization

---

## 🔗 Backend Integration Points

### Required API Access for AI

AI'ın ihtiyaç duyacağı backend endpoint'leri:

1. **Inventory:**
   - `GET /api/production/inventory/{materialId}` - Stock levels
   - `GET /api/production/inventory` - List inventory

2. **Materials:**
   - `GET /api/production/materials` - Material catalog
   - `GET /api/production/materials/{id}` - Material details

3. **Suppliers:**
   - `GET /api/procurement/suppliers` - Supplier list
   - `GET /api/procurement/suppliers/{id}` - Supplier details

4. **Tasks:**
   - `POST /api/procurement/purchase-orders` - Create PO
   - `POST /api/tasks` - Create task

**Implementation:**
- AI service, bu endpoint'leri internal olarak çağırabilir
- Function calling pattern kullanılabilir (AI → backend API → response)

---

## 💰 Cost Considerations

### LLM Provider Costs (Approximate)

| Provider | Model | Cost per 1M tokens | Notes |
|----------|-------|-------------------|-------|
| OpenAI | gpt-4o-mini | $0.15 input / $0.60 output | Recommended for MVP |
| OpenAI | gpt-4o | $2.50 input / $10.00 output | More capable, expensive |
| Anthropic | claude-3-haiku | $0.25 input / $1.25 output | Fast, cost-effective |
| Anthropic | claude-3-5-sonnet | $3.00 input / $15.00 output | Best quality |

### Cost Control Strategies

- **Rate Limiting:** Per-user/day limits
- **Token Limits:** Max tokens per request
- **Caching:** Frequent queries cached
- **Fallback:** Expensive models → cheaper models
- **Monitoring:** Cost tracking + alerts

---

## 📊 Metrics & Observability

### Key Metrics

- **Latency:** P50/P95/P99 response time
- **Cost:** Tokens used, cost per request
- **Success Rate:** API call success %
- **User Satisfaction:** Feedback (thumbs up/down)
- **Error Rate:** Failed requests

### Logging

- **Request/Response:** Prompt + response logged
- **Tool Calls:** Backend API calls logged
- **Errors:** Full error context
- **PII Redaction:** Sensitive data masked

---

## 🚀 Next Steps (Discussion Points)

1. **LLM Provider Selection:**
   - OpenAI vs Anthropic vs Local (Ollama)?
   - Budget constraints?
   - Privacy requirements?

2. **Implementation Priority:**
   - MVP: Simple Q&A only?
   - Or full function calling from start?

3. **System Message Location:**
   - Code'da static mi?
   - Database'de dynamic mi?
   - Admin UI ile yönetilebilir mi?

4. **Backend API Access:**
   - AI service internal API'leri direkt çağırsın mı?
   - Function calling pattern mi?
   - Or dedicated AI-facing endpoints?

5. **Conversation Management:**
   - Multi-turn conversations gerekli mi?
   - Conversation history nerede saklanacak? (Redis, DB?)

---

## ✅ Decision Matrix

| Aspect | Option 1: OpenAI | Option 2: Anthropic | Option 3: Local (Ollama) |
|--------|-----------------|---------------------|-------------------------|
| **Setup Complexity** | ⭐ Easy | ⭐ Easy | ⭐⭐⭐ Complex |
| **Cost** | $$ Medium | $$ Medium | $ Low (infrastructure) |
| **Performance** | ⭐⭐⭐ Excellent | ⭐⭐⭐ Excellent | ⭐⭐ Good |
| **Privacy** | ⭐⭐ Good (API) | ⭐⭐ Good (API) | ⭐⭐⭐ Excellent (On-prem) |
| **Quality** | ⭐⭐⭐ Excellent | ⭐⭐⭐ Excellent | ⭐⭐ Good |
| **Recommended For** | MVP + Production | Production | High privacy requirements |

---

**Status:** ✅ Analysis Complete - Ready for Discussion  
**Next:** User ile provider selection ve implementation details konuşulacak

