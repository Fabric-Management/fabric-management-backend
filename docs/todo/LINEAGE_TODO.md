# Lineage Module – TODO & Backlog

This document lists planned work and technical debt for the Lineage (traceability) module. For architecture context, see [../architecture/LINEAGE_ARCHITECTURE.md](../architecture/LINEAGE_ARCHITECTURE.md).

---

## 1. IWM (Inventory & Warehouse Management) Stock Integration

Currently, `BatchLineageService.create` checks that the parent batch has sufficient quantity but does **not** perform an actual stock consumption.

**TODO:**

- When a lineage record is created, either:
  - Have a listener for `BatchLineageCreatedEvent` call into IWM to record consumption, or
  - Use a Saga / distributed transaction to coordinate lineage creation and stock deduction.
- Ensure the Batch entity’s `consumedQuantity` and status transitions (e.g. `IN_PROGRESS` → `DEPLETED`) stay in sync with IWM.
- If stock deduction fails, implement a compensating transaction (e.g. remove or mark the lineage record and surface a clear error).

---

## 2. CQRS and Read Model (Graph DB)

Trace backward/forward is currently implemented with **recursive CTEs** on PostgreSQL. This is fine for moderate data size; for very large or deep trees it may become a bottleneck.

**TODO:**

- **CQRS read model** – Separate read (trace queries) from write (lineage record creation/deletion).
- **Graph database** – Consume `BatchLineageCreatedEvent` and `BatchLineageDeletedEvent` to maintain a graph store (e.g. Neo4j) asynchronously.
- **Read from graph** – Serve `traceBackward` and `traceForward` from the graph DB so complex traversals remain fast at scale.

---

## 3. Event-Driven Cost and Quality Integration

**TODO:**

- **Costing** – On lineage creation, roll up consumed components’ costs to the child batch. Trigger costing logic from `BatchLineageCreatedEvent`.
- **Quality / recall** – When a parent batch fails quality or is recalled, use events to automatically put all child batches that consumed it on hold (e.g. status = ON_HOLD) or flag them for review.

---

## See also

- [INDEX.md](INDEX.md) – List of all TODO documents.
