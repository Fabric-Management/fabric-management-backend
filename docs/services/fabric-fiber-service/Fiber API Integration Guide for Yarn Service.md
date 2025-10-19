🧩 Fiber API Integration Guide for Yarn Service

Version: 1.0
Date: 2025-10-19
Status: ✅ ACTIVE — Production Interface
Source Service: fabric-fiber-service
Consumer Service: fabric-yarn-service
Integration Type: Event + REST Hybrid (Choreography + API Validation)

🎯 PURPOSE

Bu doküman fabric-yarn-service ile fabric-fiber-service arasındaki etkileşimi tanımlar.

Yarn Service, fiber kimliği doğrulama, blend kompozisyon kontrolü, ve fiber metadata cache işlemleri için Fiber Service’i kullanır.

🔁 INTEGRATION OVERVIEW
Type	Direction	Mechanism	Purpose
Event Subscription	Fiber → Yarn	Kafka Topic	Fiber updates & lifecycle events
Synchronous API Call	Yarn → Fiber	REST (Internal Endpoint)	Fiber validation & lookup
Cache Synchronization	Yarn local Redis	Event-driven refresh	Low-latency access
🧱 DOMAIN RELATIONSHIP
[Fiber Service] ── FiberDefined ─▶ [Yarn Service]
       │                           │
       │                           ├─ validateFiberComposition() → Fiber API
       │                           ├─ storeFiberMetadataInCache()
       │                           └─ listenFiberUpdated(), FiberDeactivated()


Yarn Service hiçbir zaman Fiber verisini manuel olarak oluşturmaz —
tüm fiber referansları Fiber Service’teki canonical registry’den gelir.

1️⃣ EVENT-DRIVEN INTEGRATION (CHOREOGRAPHY)
Kafka Topics
Topic Name	Publisher	Consumer	Description
fiber.defined.v1	Fiber Service	Yarn Service	New fiber (PURE / BLEND) registered
fiber.updated.v1	Fiber Service	Yarn Service	Physical / sustainability update
fiber.deactivated.v1	Fiber Service	Yarn Service	Fiber made inactive (soft delete)
Common Event Headers
Header	Description
X-Tenant-Id	Always global (0000-...)
X-Correlation-Id	Traceability between services
X-Event-Type	fiber.defined.v1 / updated.v1 / deactivated.v1
X-Event-Timestamp	UTC timestamp
Example Payload: fiber.defined.v1
{
  "fiberId": "fbbaf3dc-7a62-4b33-9d7f-3d2852b1ac54",
  "code": "CO",
  "name": "Cotton",
  "category": "NATURAL",
  "compositionType": "PURE",
  "sustainabilityType": "CONVENTIONAL",
  "originType": "UNKNOWN",
  "status": "ACTIVE",
  "components": null,
  "createdAt": "2025-10-19T09:44:32Z",
  "version": 1
}

Yarn Service Consumer Implementation (Sample)
@KafkaListener(topics = "fiber.defined.v1", groupId = "yarn-fiber-sync")
public void handleFiberDefined(FiberDefinedEvent event) {
    fiberCache.store(event.getFiberId(), event);
    log.info("[FiberSync] New fiber registered: {}", event.getCode());
}

2️⃣ SYNCHRONOUS INTEGRATION (VALIDATION API)

Yarn Service yeni bir Yarn tanımı oluştururken Fiber bileşenlerini doğrulamak için Fiber Service’e REST isteği gönderir.

Endpoint

POST /api/v1/fibers/internal/validate

Purpose:
Bir Yarn içindeki tüm fiber bileşenlerinin geçerliliğini kontrol eder.

Request
{
  "fibers": [
    { "fiberCode": "CO", "percentage": 60 },
    { "fiberCode": "PE", "percentage": 40 }
  ]
}

Response (Success)
{
  "success": true,
  "errors": [],
  "validatedFibers": [
    {
      "fiberCode": "CO",
      "category": "NATURAL",
      "status": "ACTIVE",
      "sustainabilityType": "CONVENTIONAL"
    },
    {
      "fiberCode": "PE",
      "category": "SYNTHETIC",
      "status": "ACTIVE",
      "sustainabilityType": "CONVENTIONAL"
    }
  ]
}

Response (Failure)
{
  "success": false,
  "errors": [
    { "fiberCode": "CO", "reason": "Fiber inactive" },
    { "fiberCode": "XX", "reason": "Fiber not found" }
  ]
}

Security
Mechanism	Description
X-Internal-API-Key	Shared key verified via Gateway
X-Correlation-Id	Propagated for tracing
JWT	❌ Not required (internal-only)
3️⃣ CACHE SYNCHRONIZATION STRATEGY
Operation	Trigger	Action in Yarn Service
FiberDefined	Kafka event	Insert new fiber into Redis
FiberUpdated	Kafka event	Update cached fiber metadata
FiberDeactivated	Kafka event	Remove from cache
Cache miss	REST lookup	Fetch via /internal/batch
Example Key Design
fiber:{fiberCode} → cached JSON
fiber:list:active → set of active fiber codes


TTL controlled by environment:

FIBER_CACHE_TTL: 3600
FIBER_CACHE_MAX_SIZE: 1000

4️⃣ CONTRACT ALIGNMENT (AGGREGATE FIELD MAPPING)
Fiber Field	Yarn Field (in YarnSpecification)	Type	Description
fiberCode	component.fiberCode	String	Primary reference
category	category	Enum	Inherited automatically
compositionType	compositionType	Enum	Alignment check
sustainabilityType	component.sustainabilityType	Enum	Optional override
status	validation rule	Enum	Must be ACTIVE
5️⃣ ERROR HANDLING & RETRY POLICY
Failure Type	Source	Recovery
FiberNotFoundException	Fiber API	Yarn aborts with 400, not retried
Kafka deserialization	Event listener	Dead-letter queue (DLQ: fiber.events.dlq)
Network timeout	FeignClient	Retry 3× exponential backoff
Cache sync failure	Redis	Logged + auto rebuild on next event
6️⃣ OBSERVABILITY & TRACEABILITY
Tool	Description
OpenTelemetry	Spans propagate across Fiber → Yarn
CorrelationId	Logged at both service sides
Structured Logging	{service, eventType, fiberCode, correlationId}
Metrics	fiber_lookup_latency_ms, fiber_cache_hit_ratio, fiber_event_delay_ms
7️⃣ LOCAL DEVELOPMENT FLOW

1️⃣ Start Kafka & Redis (docker compose)
2️⃣ Start fabric-fiber-service (port 8094)
3️⃣ Start fabric-yarn-service (port 8095)
4️⃣ Run:

curl -X POST http://localhost:8095/api/v1/yarns \
  -H "Content-Type: application/json" \
  -d '{"components":[{"fiberCode":"CO","percentage":100}]}'


✅ YarnService → FiberService validation → FiberDefined event consumed.

8️⃣ TEST SCENARIOS (Integration Tests)
ID	Scenario	Expected Result
FIB-YRN-001	Create yarn with active fibers	201 Created
FIB-YRN-002	Create yarn with inactive fiber	400 Validation error
FIB-YRN-003	Fiber updated → Yarn cache refresh	Cache key invalidated
FIB-YRN-004	Fiber deactivated → Yarn removes from list	Yarn logs deactivation
FIB-YRN-005	Kafka downtime	DLQ populated, reprocessed later
9️⃣ DEPLOYMENT NOTES
Config	Value	Source
FIBER_SERVICE_URL	http://fiber-service:8094	Env var
KAFKA_TOPIC_FIBER_DEFINED	fiber.defined.v1	Shared constants
REDIS_HOST	redis	Shared infra
DLQ_TOPIC	fiber.events.dlq	Error recovery

All parameters environment-driven → ZERO hardcoded.

🔒 SECURITY & POLICY

All internal APIs protected by Gateway X-Internal-API-Key

No external exposure

Fiber data immutable except via Fiber Service

Multi-tenant compliance not required (global registry)

✅ SUMMARY
Feature	Status
Event-driven sync	✔
REST validation	✔
Cache strategy	✔
Error resilience	✔
Tracing & metrics	✔
Zero hardcoding	✔
Over-engineering	❌ none

🧠 Result:
Yarn Service stays fully consistent with the canonical Fiber registry.
Integration is asynchronous by default, synchronous when needed, and observably safe.
Clean, scalable, and production-ready.