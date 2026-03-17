# Frontend–Backend Contract Audit — Batch & Lineage Types

**Scope:** BatchDto, CreateBatchRequest, BatchLineageDto, CreateBatchLineageRequest, batch JSONB attribute interfaces (FiberAttributes, YarnAttributes), and inheritance-engine types.

**Source of truth:** Backend Java DTOs (actual files). Frontend: `types/production/batch-execution.ts`, `types/production/batch-attributes.ts`, `types/production/enums.ts`.

---

## Findings

### 1. KEY NAMING (JSONB attributes)

**[CRITICAL] KEY NAMING — FiberAttributes uses camelCase; API returns snake_case**
- **File:** `fabric-management-frontend/src/types/production/batch-attributes.ts`
- **Backend:** JSONB `BatchDto.attributes` keys are snake_case: `fiber_micronaire`, `fiber_staple_length`, `fiber_grade`, `fiber_shade`, `fiber_organic_cert_no` (set by BatchService.resolveAttributes and inheritance engine).
- **Frontend:** `FiberAttributes` defines `micronaire`, `stapleLength`, `fiberGrade`, `fiberShade`, `organicCertNo` (camelCase).
- **Impact:** Any code using `batch.attributes.micronaire` or `batch.attributes.fiberGrade` after `isFiberBatch(batch)` gets **always undefined**. API returns `attributes["fiber_micronaire"]` etc. Silent runtime bug; type guard gives false sense of safety.

**[CRITICAL] KEY NAMING — YarnAttributes uses camelCase; API will use snake_case**
- **File:** `fabric-management-frontend/src/types/production/batch-attributes.ts`
- **Backend:** Yarn JSONB keys (fiber-to-yarn.json, future YarnService): `yarn_count`, `twist_direction` (snake_case).
- **Frontend:** `YarnAttributes` defines `yarnCount`, `twistDirection` (camelCase).
- **Impact:** Same as above: `batch.attributes.yarnCount` and `batch.attributes.twistDirection` will always be undefined at runtime when Yarn module returns data.

**[MEDIUM] KEY NAMING — YarnAttributes missing inherited fiber keys**
- **File:** `fabric-management-frontend/src/types/production/batch-attributes.ts`
- **Backend:** Inheritance engine and fiber-to-yarn.json set on Yarn batches: `raw_fiber_grade`, `raw_fiber_shade`, `fiber_micronaire`, `fiber_staple_length`, `fiber_organic_cert_no` (array possible).
- **Frontend:** `YarnAttributes` only has `twistDirection`, `yarnCount` and index signature.
- **Impact:** No type safety for inherited fiber fields on Yarn batches; partial safety.

---

### 2. MISSING FIELDS

**[HIGH] MISSING FIELDS — CreateBatchRequest missing fiber-specific fields**
- **File:** `fabric-management-frontend/src/types/production/batch-execution.ts`
- **Backend:** `CreateBatchRequest` has optional: `micronaire` (Double), `stapleLength` (Double), `fiberGrade` (String), `fiberShade` (String), `organicCertNo` (String). Mapped to `attributes` with fiber_ prefix by BatchService when materialType = FIBER.
- **Frontend:** `CreateBatchRequest` has no micronaire, stapleLength, fiberGrade, fiberShade, organicCertNo.
- **Impact:** Forms must push fiber data via `attributes` with hand-written snake_case keys; no type safety; easy to mistype keys or forget mapping.

**[MEDIUM] MISSING FIELDS — CreateBatchRequest missing version**
- **File:** `fabric-management-frontend/src/types/production/batch-execution.ts`
- **Backend:** `CreateBatchRequest` has `private Long version;` (optional, for optimistic lock if used on create path).
- **Frontend:** No `version` on `CreateBatchRequest`.
- **Impact:** If backend ever uses version on create or PATCH, frontend cannot send it without breaking the typed contract.

**[MEDIUM] EXTRA FIELD — CreateBatchRequest.attributeIds not in backend**
- **File:** `fabric-management-frontend/src/types/production/batch-execution.ts`
- **Backend:** `CreateBatchRequest` has no `attributeIds` field.
- **Frontend:** `CreateBatchRequest` has `attributeIds?: string[]`.
- **Impact:** Payload key is ignored by backend; misleading contract; no type safety for what backend actually accepts.

**[MEDIUM] EXTRA FIELD — BatchDto.fiberAttributes not in backend**
- **File:** `fabric-management-frontend/src/types/production/batch-execution.ts`
- **Backend:** `BatchDto` has no `fiberAttributes` field (only `attributes` Map).
- **Frontend:** `BatchDto` has `fiberAttributes?: FiberAttributeDto[]`.
- **Impact:** Field is always undefined from API; type suggests it exists; partial/incorrect type safety.

---

### 3. MISSING TYPES

**[HIGH] MISSING TYPES — No TypeScript for inheritance engine**
- **Files:** N/A (no `inheritance-rules.ts` or equivalent).
- **Backend:** `InheritanceAction` (enum), `InheritanceRule` (record), `AttributeInheritanceSchema` (record) in lineage domain/rule.
- **Frontend:** No equivalent types.
- **Impact:** Any future UI for rule config, lineage inheritance, or debugging inheritance (e.g. FIBER→YARN) has no type safety; must use ad-hoc types or `any`.

**[MEDIUM] MISSING TYPES — BatchAttributes (Java record)**
- **Backend:** `BatchAttributes(Map<String, Object> attributes, BigDecimal quantity)` — engine input.
- **Frontend:** No equivalent.
- **Impact:** Only relevant if frontend ever constructs or displays this structure; otherwise server-only, low impact.

---

### 4. TYPE ACCURACY & OPTIONAL VS REQUIRED

**[MEDIUM] OPTIONAL VS REQUIRED — BatchLineageDto.consumedAt**
- **File:** `fabric-management-frontend/src/types/production/batch-execution.ts`
- **Backend:** `BatchLineageDto.consumedAt` is `Instant` (nullable in DB/entity).
- **Frontend:** `consumedAt: string` (required).
- **Impact:** If backend sends `null`, frontend type is wrong; runtime can be `undefined` and break code that assumes string.

**[LOW] TYPE ACCURACY — BatchDto.version / BatchLineageDto.version**
- **File:** `fabric-management-frontend/src/types/production/batch-execution.ts`
- **Backend:** `BatchDto.version` and `BatchLineageDto.version` are `Long` (nullable in JSON).
- **Frontend:** `version: number` (required).
- **Impact:** If backend ever sends null for version, frontend type is inaccurate; usually version is always present, so low impact.

**[LOW] TYPE ACCURACY — BatchDto productionDate/expiryDate**
- **Backend:** `Instant` (ISO-8601 string in JSON).
- **Frontend:** `productionDate?: string`, `expiryDate?: string`. Correct; optional matches nullable backend.

---

### 5. REQUEST CONTRACTS (WRITE-SIDE)

- **CreateBatchRequest:** See findings above (missing fiber fields, version; extra attributeIds).
- **CreateBatchLineageRequest:** Backend has `version` (Long), optional `consumptionPercentage`, optional `consumedAt`, optional `processReference`, optional `remarks`. Frontend has all of these as optional and `version?: number`. Match is acceptable; only consumedAt is required in frontend for display purposes but backend allows null — already covered under BatchLineageDto.consumedAt.

---

### 6. INHERITANCE ENGINE TYPES

- **InheritanceAction, InheritanceRule, AttributeInheritanceSchema:** No TypeScript representation (see Missing Types).
- **JSONB attribute keys** used by the engine (fiber_*, raw_fiber_*, yarn_*, bale_moisture) are documented in backend and fiber-to-yarn.json but not reflected in frontend `FiberAttributes` / `YarnAttributes` key names (see Critical key naming findings).

---

## Prioritized Fix Plan

| Priority | Action | Findings addressed |
|----------|--------|--------------------|
| **P0 — Fix immediately** | Align JSONB attribute keys with backend: either (a) define FiberAttributes/YarnAttributes with **snake_case** keys matching API (`fiber_micronaire`, `fiber_staple_length`, `fiber_grade`, `fiber_shade`, `fiber_organic_cert_no`, `yarn_count`, `twist_direction`, plus inherited `raw_fiber_grade`, `raw_fiber_shade`) or (b) keep camelCase in TS and add a thin mapping layer that reads/writes snake_case from/to API. Option (a) avoids silent undefined at runtime. | CRITICAL key naming (Fiber + Yarn) |
| **P1 — Fix before Yarn module** | Add fiber-specific optional fields to `CreateBatchRequest`: `micronaire`, `stapleLength`, `fiberGrade`, `fiberShade`, `organicCertNo`. Remove or clearly document `attributeIds` (not in backend). Add TS types for InheritanceAction, InheritanceRule, AttributeInheritanceSchema if any lineage/inheritance UI is planned. Extend YarnAttributes with inherited fiber keys. | HIGH missing CreateBatchRequest fiber fields; HIGH missing inheritance types; MEDIUM YarnAttributes incomplete |
| **P2 — Fix eventually** | Add `version?: number` to CreateBatchRequest if backend contract uses it. Remove `fiberAttributes` from BatchDto or add backend support and align. Mark `BatchLineageDto.consumedAt` as `string \| null` or `string \| undefined` if backend can return null. Document or allow `version` null on BatchDto/BatchLineageDto if backend can omit it. | MEDIUM extra/missing fields; MEDIUM consumedAt optional; LOW version nullability |

---

## Summary Table

| Severity | Category | File(s) | Count |
|----------|----------|---------|-------|
| CRITICAL | Key naming (JSONB ≠ API keys) | batch-attributes.ts | 2 |
| HIGH | Missing request fields / missing types | batch-execution.ts, batch-attributes.ts, (no inheritance-rules.ts) | 3 |
| MEDIUM | Extra field, optional/required mismatch, incomplete attribute type | batch-execution.ts, batch-attributes.ts | 5 |
| LOW | Version nullability, JSDoc/naming | batch-execution.ts | 2 |

**Total findings:** 12. **Do not fix in this audit;** report only as requested.
