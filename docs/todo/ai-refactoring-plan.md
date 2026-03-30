# AI Module Refactoring Plan: AIFunctionCaller Decoupling

`AIFunctionCaller.java` (1199 lines) currently acts as a "God Object" with direct binary dependencies on `production` module repositories and facades. This document outlines a two-phase plan to break these dependencies using the `AIToolRegistry` pattern.

## Phase 1: Separating Pure Domain Tools (Sprint 3.1)

**Objective:** Move single-domain tools into their respective modules, cleaning up the most direct dependency violations.

### 1.1 FiberAIToolProvider (`production/masterdata/fiber`)
- **Supported Tools:** `search_fibers`, `get_fiber_info`, `list_fiber_categories`, `create_fiber`.
- **Constraint:** Must use `FiberFacade` or `FiberQueryService`. No direct `FiberRepository` access (fixes Constitution violation).
- **Relocation:** Move logic for these tools from `AIFunctionCaller` to this provider.

### 1.2 MaterialAIToolProvider (`production/masterdata/material`)
- **Supported Tools:** `check_material_stock`, `create_material`.
- **Constraint:** Use `MaterialFacade`.
- **Relocation:** Move logic for these specific tools.

### 1.3 AIFunctionCaller Delegation
- Inject `AIToolRegistry` into `AIFunctionCaller`.
- Replace existing private methods with calls to `toolRegistry.execute(tenantId, toolName, parameters)`.
- **Success Criteria:** Removal of `FiberRepository` and `FiberCategoryRepository` imports from `platform/ai`.

---

## Phase 2: Orchestration Tools & Infrastructure (Sprint 3.2)

**Objective:** Isolate multi-domain orchestration and centralize common metadata/caching logic.

### 2.1 SmartSearchTool (`platform/ai/app`)
- **Objective:** Move `smart_search` and `get_production_status` to a dedicated `AIToolProvider` implementation within the AI module.
- **Independence:** This tool should NOT import `FiberFacade` or `MaterialFacade` directly. It should use `AIToolRegistry.execute()` to call the domain-specific tools discovered in Phase 1.
- **Normalization:** Relocate `normalizeFiberQuery` and Turkish-to-English translation logic to `common/infrastructure/ai/AIQueryNormalizer`.

### 2.2 Shared Infrastructure
- **Centralized Caching:** Move `functionResultCache` and logic into `AIToolRegistry.execute`. 
    - Registry handles the "Check Cache -> Call Provider -> Store Cache" flow.
    - Exclude `create_*` tools from cache (mutating operations).
- **Cross-Domain Reference:** Refactor `search_materials` (which currently cross-references fiber names) to use `toolRegistry.execute("get_fiber_info", ...)` instead of `FiberFacade`.

### 2.3 ArchUnit Enforcement
- Implement **Rule 11.4** in `ConstitutionArchTest.java`.
- Final verification that `platform/ai` has ZERO imports from `production/` or other domain-specific packages.

---

## Implementation Progress

| Feature | Phase | Status |
|---------|-------|--------|
| AIToolProvider Interface | Foundational | ✅ Done |
| AIToolRegistry Implementation | Foundational | ✅ Done |
| FiberAIToolProvider | Phase 1 | ⏳ Pending |
| MaterialAIToolProvider | Phase 1 | ⏳ Pending |
| SmartSearchRefactor | Phase 2 | ⏳ Pending |
| Centralized Caching | Phase 2 | ⏳ Pending |
| ArchUnit Rule 11.4 | Phase 2 | ⏳ Pending |
