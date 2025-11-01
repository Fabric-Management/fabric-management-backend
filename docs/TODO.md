# TODO - Technical Improvements & Future Enhancements

**Last Updated:** 2025-11-01  
**Status:** Active  
**Purpose:** Track technical debt, improvements, and future enhancements that add value to the application.

---

## 📊 Inventory

### Real Inventory Repository Integration
**Priority:** Medium  
**Status:** Blocked (Repository Module Not Implemented)  
**Estimated Effort:** 4-6 hours (includes repository module setup)

#### Current Situation
- `InventoryService.getStockByMaterialName()` returns placeholder data (quantity: 0, placeholder location)
- Material lookup works correctly via `MaterialFacade`
- **Blocked:** `inventory_item` and `inventory_location` tables don't exist yet
- **Blocked:** `InventoryItemRepository` and related infrastructure not implemented

#### Prerequisites
- Create `logistics.inventory_item` table with columns: `id`, `tenant_id`, `material_id`, `quantity`, `location_id`, `is_active`
- Create `logistics.inventory_location` table with columns: `id`, `tenant_id`, `location_name`, `address_id`, `is_active`
- Implement `InventoryItemRepository` with tenant-scoped queries
- Implement `InventoryLocationRepository` if needed

#### Solution (Once Repository Module Exists)
- Implement actual query to `inventory_item` table using repository
- Aggregate quantities by location if needed
- Return real location data from `inventory_location` table
- Handle multi-location stock aggregation

#### Related Files
- `src/main/java/com/fabricmanagement/logistics/inventory/app/InventoryService.java` (lines 47, 56, 58)

---

## 🤖 AI & Material Matching

### Material Matcher Enhancement (Yarn/Fabric Support)
**Priority:** Low  
**Status:** Pending  
**Estimated Effort:** 2-3 hours

#### Current Situation
- `MaterialMatcher` currently supports only `Fiber` entities
- Yarn and Fabric support planned but not implemented

#### Solution
- Add `YarnFacade` and `FabricFacade` dependencies when available
- Extend `getMaterialName()` to support Yarn and Fabric types
- Update matching logic to query all material types

#### Related Files
- `src/main/java/com/fabricmanagement/logistics/inventory/app/MaterialMatcher.java` (lines 25, 223)

---

### Production Status Function
**Priority:** Low  
**Status:** Pending  
**Estimated Effort:** 1-2 hours

#### Current Situation
- `AIFunctionCaller.getProductionStatus()` returns placeholder message
- Production status endpoint not yet implemented

#### Solution
- Implement production status endpoint/service
- Connect to production module when available
- Return real-time production metrics

#### Related Files
- `src/main/java/com/fabricmanagement/common/platform/ai/app/AIFunctionCaller.java` (line 179)

---

## 🧪 Testing

### Unit Tests for InventoryService
**Priority:** Medium  
**Status:** Planned  
**Estimated Effort:** 3-4 hours

#### Required Tests
- `InventoryService.getStockByMaterialName()` integration tests
- `MaterialMatcher` unit tests
- `AIFunctionCaller.checkMaterialStock()` integration tests

---

## 📝 Documentation

### API Documentation Updates
**Priority:** Low  
**Status:** Planned

#### Tasks
- Update Swagger/OpenAPI docs for new endpoints
- Document JWT context requirements (handled by `JwtContextInterceptor`)
- Add examples for tenant-aware endpoints

---

## 🎯 Future Considerations

### Performance Optimizations
- Cache frequently accessed tenant/user context
- Optimize database queries for multi-tenant scenarios

### Security Enhancements
- Rate limiting per tenant
- Audit logging for context changes
- Token refresh mechanism

---

**Note:** This list prioritizes items that:
1. Add real value to the application
2. Are not hardcoded/temporary solutions
3. Follow production-ready standards
4. Reduce technical debt
