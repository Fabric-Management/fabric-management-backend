---
name: Bullet-proof Architecture & Refactoring (Faz 3 Standards)
description: Guidelines for ensuring strict authorization, data integrity, bounded context decoupling, and event-driven state transitions when implementing or reviewing modules.
---

# Bullet-proof Architecture & Refactoring Standards

This skill defines the "Bullet-proof" standards applied during the implementation and code review of features, particularly for module integrations (such as Faz 3 Mal Kabul and Satın Alma). When implementing new modules or refactoring existing ones in the Fabric Management System, follow this checklist strictly.

## 1. Strict Authorization (Security First)
- **Check API Controllers:** Ensure EVERY endpoint inside a controller is protected with `@PreAuthorize`.
- **Role + Department Matrix:** The system uses a Soft-Matrix authorization model. Ensure the correct `AccessService` (e.g., `ProductionAccessService`, `ProcurementAccessService`) handles the logic using both the user's role and their associated `DepartmentCode`.
- **Action-Based Protection:** Being authenticated is not enough. You must write explicit permissions mapping `READ` vs `WRITE` actions to proper departments.

## 2. Unbreakable Data Integrity
- **Calculate, Don't Trust Inputs:** Never blindly accept parent-level aggregated fields (e.g., `totalWeight`, `netWeight`, `grossWeight`) raw from API input if they can be calculated from child items. Calculate them dynamically on the backend.
- **State Machine Validation:** Always check if domain entity state transitions are valid before proceeding (e.g., `status.canTransitionTo(...)`). Reject arbitrary status changes.

## 3. Bounded Context Decoupling (Facade/QueryService Pattern)
- **No Cross-Module Repository Calls:** A Service belonging to Module A must NEVER inject a Repository belonging to Module B (e.g., `GoodsReceiptService` must never use `PurchaseOrderRepository` directly).
- **Use Facades (Query Services):** Module B should expose a read-only `QueryService` (e.g., `PurchaseOrderQueryService`) acting as a contract. Module A uses this QueryService to retrieve essential cross-module data (like entity IDs and Codes).

## 4. Event-Driven State Transitions (EDA)
- **Avoid Synchronous Tangling:** When finalizing a high-level domain action (e.g., Confirming a Receipt), do not embed side-effects (like modifying cross-module stock inventory or PO statuses) directly into the same method.
- **Publish Domain Events:** Once the local change is completed and saved, publish a rich Domain Event using Spring's `ApplicationEventPublisher` (e.g., `GoodsReceiptConfirmedEvent`).
- **Decoupled Listeners:** Let other modules listen to this event asynchronously (via `@EventListener` or `@TransactionalEventListener`) to perform their side-effects independently, ensuring module isolation.

---
**Usage:** Keep this checklist in mind before concluding any "Phase" or marking a module implementation as "done".
