# Batch-Related Frontend Consumer Audit

**Scope:** All components and hooks that consume batch-related types (BatchDto, CreateBatchRequest, lineage, attributes).

**Discovery:** Files that import from `@/types` (or types/production) and use BatchDto, CreateBatchRequest, BatchLineageDto, LineageNodeDto, TraceNodeDto, or batch attribute config.

**Audited files:**
- `src/features/production/batch/config/material-attributes.ts`
- `src/features/production/batch/components/CreateBatchModal.tsx`
- `src/features/production/batch/components/BatchDetailDrawer.tsx`
- `src/features/production/batch/components/BatchesTab.tsx`
- `src/features/production/batch/components/BatchLineagePanel.tsx`
- `src/features/production/batch/components/CreateBatchLineageModal.tsx`
- `src/features/production/batch/components/LineageGraph.tsx`
- `src/features/production/batch/services/batch.service.ts`
- `src/features/production/batch/services/lineage.service.ts`
- `src/features/production/inventory/components/InventoryKpiDashboard.tsx`
- `src/features/production/fiber/components/FiberCatalog.tsx`
- `src/features/production/warehouse/components/WipBoard.tsx`
- `src/features/production/batch/components/AddBatchCertificationModal.tsx`
- `src/features/production/batch/components/BatchQuantityModal.tsx`
- `src/features/production/batch/components/StartProductionModal.tsx`

---

## Findings

### [CRITICAL] CHECK 1 — JSONB attribute keys are camelCase in config; API uses snake_case

**File:** `fabric-management-frontend/src/features/production/batch/config/material-attributes.ts`  
**Line(s):** 21–76  

**Code:**  
```ts
FIBER: [
  { key: "micronaire", label: "Micronaire", ... },
  { key: "stapleLength", label: "Staple Length (mm)", ... },
  ...
],
YARN: [
  { key: "twistDirection", ... },
  { key: "yarnCount", ... },
],
```

**Backend:** `BatchDto.attributes` and backend JSONB use **snake_case** keys: `fiber_micronaire`, `fiber_staple_length`, `fiber_grade`, `fiber_shade`, `fiber_organic_cert_no`, `yarn_count`, `twist_direction`.  

**Frontend:** `MATERIAL_ATTRIBUTE_CONFIG` uses **camelCase** keys (`micronaire`, `stapleLength`, `twistDirection`, `yarnCount`, etc.).  

**Impact:** Any code that does `batch.attributes[attr.key]` with `attr` from this config will always read `undefined`, because the API never returns those camelCase keys.  

**Fix:** Change config keys to the backend’s snake_case (and material prefix where applicable), e.g. for FIBER: `fiber_micronaire`, `fiber_staple_length`; for YARN: `twist_direction`, `yarn_count`. Then ensure CreateBatchModal and any code building `attributes` use the same keys.

---

### [CRITICAL] CHECK 1 — BatchDetailDrawer reads attributes with config keys (camelCase → undefined)

**File:** `fabric-management-frontend/src/features/production/batch/components/BatchDetailDrawer.tsx`  
**Line(s):** 248–259  

**Code:**  
```tsx
{attrConfig.map((attr) => (
  ...
  <p className="...">
    {batch.attributes?.[attr.key] != null
      ? String(batch.attributes[attr.key])
      : "—"}
  </p>
))}
```

**Backend:** Returns `attributes["fiber_micronaire"]`, `attributes["fiber_staple_length"]`, etc.  

**Frontend:** `attrConfig` comes from `MATERIAL_ATTRIBUTE_CONFIG[batch.materialType]`, so `attr.key` is `"micronaire"`, `"stapleLength"`, etc.  

**Impact:** Material attributes in the overview tab always show "—" because `batch.attributes["micronaire"]` etc. are undefined.  

**Fix:** Either (1) change `MATERIAL_ATTRIBUTE_CONFIG` to use snake_case keys (see above), or (2) in this file only, map materialType + attr.key to the backend key (e.g. FIBER + "micronaire" → `"fiber_micronaire"`) when reading.

---

### [CRITICAL] CHECK 1 — BatchesTab table cells read attributes with config keys (camelCase → undefined)

**File:** `fabric-management-frontend/src/features/production/batch/components/BatchesTab.tsx`  
**Line(s):** 584–587  

**Code:**  
```tsx
{batch.attributes?.[attr.key] != null
  ? String(batch.attributes[attr.key])
  : "—"}
```
(Inside a table cell, with `attr` from `attrConfig` = MATERIAL_ATTRIBUTE_CONFIG.)

**Backend:** Same as above — API sends snake_case keys in `attributes`.  

**Frontend:** `attr.key` is camelCase from `MATERIAL_ATTRIBUTE_CONFIG`.  

**Impact:** Attribute columns in the batch table always show "—".  

**Fix:** Align with backend keys: use snake_case (and prefix) in config, or map `attr.key` to the API key when reading here.

---

### [CRITICAL] CHECK 2 — CreateBatchRequest.attributeIds used but not in backend

**File:** `fabric-management-frontend/src/features/production/batch/components/CreateBatchModal.tsx`  
**Line(s):** 150, 242, 281, 470–472  

**Code:**  
- State (150): `const [attributeIds, setAttributeIds] = useState<string[]>([]);`  
- Request (242): `attributeIds: attributeIds.length > 0 ? attributeIds : undefined,`  
- Reset (281): `setAttributeIds([]);`  
- UI (470–472): `<AttributeGroupSelector ... selectedIds={attributeIds} onChange={setAttributeIds} />`  

**Backend:** `CreateBatchRequest` has **no** `attributeIds` field.  

**Frontend:** Type and UI send `attributeIds` in the payload.  

**Impact:** Backend ignores the field; link between batch and master-data attributes is not persisted. Misleading contract and dead code path.  

**Fix:** Remove `attributeIds` from CreateBatchRequest type and from this modal (state, request object, reset, and AttributeGroupSelector usage) unless/until backend supports it; or add support on the backend and keep the field.

---

### [HIGH] CHECK 3 — CreateBatchRequest fiber fields not used; form uses generic attributes with wrong keys

**File:** `fabric-management-frontend/src/features/production/batch/components/CreateBatchModal.tsx`  
**Line(s):** 233–242 (handleSave: cleanAttributes + request), 281 (reset attributes), 470–472 (AttributeGroupSelector)  

**Code:**  
- Request is built with `attributes: cleanAttributes` where keys come from `MATERIAL_ATTRIBUTE_CONFIG` (e.g. `micronaire`, `stapleLength`).  
- No assignment to `micronaire`, `stapleLength`, `fiberGrade`, `fiberShade`, `organicCertNo` on the request.  

**Backend:** `CreateBatchRequest` has optional `micronaire`, `stapleLength`, `fiberGrade`, `fiberShade`, `organicCertNo`. `BatchService.resolveAttributes()` maps these into `attributes` with `fiber_` prefix. It does **not** read from `request.getAttributes()` for those fiber keys.  

**Frontend:** Sends only `attributes: { micronaire: 4.2, stapleLength: 28 }` (camelCase). Backend never maps those into `fiber_micronaire` / `fiber_staple_length`.  

**Impact:** Fiber-specific data in the create form is not persisted as intended; backend expects top-level fiber fields or would need to be changed to also read from `attributes` with a key mapping.  

**Fix:** For FIBER, set the typed fiber fields on the request from the form (e.g. from `attributes` state or dedicated state): `micronaire`, `stapleLength`, `fiberGrade`, `fiberShade`, `organicCertNo`. Optionally still send `attributes` for any extra keys; ensure keys in `attributes` are snake_case if backend is ever extended to merge them.

---

### [LOW] CHECK 5 — Inheritance types not used in lineage UI

**File:** N/A (multiple: BatchLineagePanel, CreateBatchLineageModal, lineage services)  

**Backend:** InheritanceAction, InheritanceRule, AttributeInheritanceSchema exist and are used by the engine.  

**Frontend:** `InheritanceAction`, `InheritanceRule`, `AttributeInheritanceSchema` are exported from `@/types/production` but no component or service imports them.  

**Impact:** No type safety for future lineage/rule config UI (e.g. displaying or editing inheritance rules).  

**Fix:** When adding UI for rule config or inheritance metadata, import and use these types instead of ad-hoc shapes or `any`.

---

## Summary Table

| Severity | Count | Files affected |
|----------|-------|----------------|
| CRITICAL | 4     | material-attributes.ts, BatchDetailDrawer.tsx, BatchesTab.tsx, CreateBatchModal.tsx |
| HIGH     | 1     | CreateBatchModal.tsx |
| LOW      | 1     | (future lineage/rule UI) |

**Clean (no issues in this audit):**  
BatchLineagePanel.tsx, CreateBatchLineageModal.tsx, LineageGraph.tsx, batch.service.ts, lineage.service.ts, InventoryKpiDashboard.tsx, FiberCatalog.tsx, WipBoard.tsx, AddBatchCertificationModal.tsx, BatchQuantityModal.tsx, StartProductionModal.tsx.

**Not applicable:**  
- CHECK 2 `fiberAttributes`: no component reads `batch.fiberAttributes`.  
- CHECK 4 `consumedAt`: no code found that uses `consumedAt` from lineage DTOs.

---

## Fix Priority

1. **P0 — Fix immediately (CRITICAL)**  
   - **material-attributes.ts:** Change all keys in `MATERIAL_ATTRIBUTE_CONFIG` to backend snake_case (and `fiber_` / `yarn_` prefix where applicable). This single change fixes both BatchDetailDrawer and BatchesTab attribute display.  
   - **CreateBatchModal.tsx:** Remove `attributeIds` from state, request object, reset, and AttributeGroupSelector (or document and keep only if backend will support it). Ensure FIBER create sends data in a way backend can persist (see P1).

2. **P1 — Fix before relying on fiber attributes (HIGH)**  
   - **CreateBatchModal.tsx:** When `materialType === "FIBER"`, populate CreateBatchRequest with the typed fiber fields (`micronaire`, `stapleLength`, `fiberGrade`, `fiberShade`, `organicCertNo`) from the form (either from current `attributes` state with a key map, or from dedicated fields). Ensure the payload matches backend’s expectation (top-level fields and/or snake_case in `attributes` if backend is updated).

3. **P2 — When adding rule/inheritance UI (LOW)**  
   - Import and use `InheritanceAction`, `InheritanceRule`, `AttributeInheritanceSchema` from `@/types/production` in any new lineage or rule-config components.

---

**End of report. No code was changed; audit only.**
