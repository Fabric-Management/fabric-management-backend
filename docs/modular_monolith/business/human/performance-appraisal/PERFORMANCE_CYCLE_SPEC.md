# Performance Cycle Specification

## Configuration
- `cycleCode`, `year`, `frequency (annual|biannual|quarterly)`
- `ratingScale` (e.g., 1-5 with label mapping)
- `calibrationRequired` boolean
- `lockDate` when edits cease

## Workflow
1. Launch cycle → notify managers/employees.
2. Self-review submission.
3. Manager review & rating proposal.
4. Calibration session adjusts ratings (if enabled).
5. Finalization exports ratings to compensation module.

## Normalization Rule
- Ensure distribution stays within configured guardrails (e.g., max 10% top tier).
- Violations flagged as findings requiring HR approval.

