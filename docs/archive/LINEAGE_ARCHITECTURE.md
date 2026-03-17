# Lineage (Traceability) Module – Architecture Overview

This document describes the design and integration points of the Lineage module in FabricOS. It supports end-to-end traceability (e.g. fibre → yarn → fabric) and is used for root-cause analysis, recalls, and compliance.

## Current Design

- **BatchLineage** links two batches: a *parent* (consumed) and a *child* (produced). It is material-agnostic; the same model supports fibre→yarn, yarn→fabric, and blending (multiple parents → one child).
- **Trace backward** – From a batch, traverse parents to find all raw materials that went into it (e.g. which fibres produced this yarn).
- **Trace forward** – From a batch, traverse children to find all products made from it (e.g. which yarns and fabrics used this fibre).
- Queries are implemented today with **recursive CTEs** on PostgreSQL. This is sufficient for moderate scale; for very large or deep graphs, a dedicated read model (e.g. graph DB) is planned (see [todo/LINEAGE_TODO.md](../todo/LINEAGE_TODO.md)).

## Integration Points

- **IWM (Inventory & Warehouse Management)** – When a lineage record is created, parent batch consumption should be reflected in stock (consumed quantity, status transitions). Today this is partially aligned; full automation and compensating transactions are in the backlog.
- **Events** – `BatchLineageCreatedEvent` and `BatchLineageDeletedEvent` are the hooks for downstream systems (costing, quality, graph sync).
- **Costing** – Consumed components’ costs can be rolled up to the child batch when lineage is created; event-driven integration is planned.
- **Quality / Recall** – When a parent fails quality or is recalled, event-driven flows can quarantine or flag all child batches that used it.

For concrete backlog items (IWM sync, CQRS, graph DB, costing, quality), see [../todo/LINEAGE_TODO.md](../todo/LINEAGE_TODO.md).
