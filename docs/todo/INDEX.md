# TODO & Backlog Index

This folder holds per-module backlog and TODO documents. Use this index to find where each area is tracked.

| Document | Scope | Summary |
|----------|--------|---------|
| [LINEAGE_TODO.md](LINEAGE_TODO.md) | Lineage (traceability) | IWM stock sync, CQRS/graph read model, costing and quality event integration (English) |
| [LINEAGE_ARCHITECTURE_TODO_LEGACY.md](LINEAGE_ARCHITECTURE_TODO_LEGACY.md) | Lineage | Original Turkish TODO/plan; superseded by LINEAGE_TODO.md + [../architecture/LINEAGE_ARCHITECTURE.md](../architecture/LINEAGE_ARCHITECTURE.md) |
| [OPTIMISTIC_LOCKING_TODO.md](OPTIMISTIC_LOCKING_TODO.md) | Concurrency | DTO version, 409 details, frontend types, conflict UI, modüllere yayılım |
| [CQRS_TODO.md](CQRS_TODO.md) | CQRS / Read scaling | Paket yapısı, cache, read projection, read replica, Redis |
| [PRODUCTION_SYSTEM_ROADMAP_TODO.md](PRODUCTION_SYSTEM_ROADMAP_TODO.md) | Üretim roadmap | QC/karantina, state machine, event-driven, task/Scrumban, rule engine |
| [BATCH_GENERALIZATION_TODO.md](BATCH_GENERALIZATION_TODO.md) | Batch model | FiberBatch → Batch: DB, backend, frontend refactor |
| [WIP_LOCATION_TODO.md](WIP_LOCATION_TODO.md) | WIP / Lokasyon | Makine = lokasyon, transfer vs consume |
| [CROSS_MODULE_LINEAGE_TODO.md](CROSS_MODULE_LINEAGE_TODO.md) | Lineage (cross-module) | BatchLineage senaryoları, trace kuralları, UI |

---

## Adding a new TODO document

1. Create a new file in `todo/` (e.g. `BATCH_TODO.md`, `INVENTORY_TODO.md`).
2. Add a row to the table above in this `INDEX.md`.
3. Optionally add a short architecture or context doc under `../architecture/` and link to it from the TODO file.
