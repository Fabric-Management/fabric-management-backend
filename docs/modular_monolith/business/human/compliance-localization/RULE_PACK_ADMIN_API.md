# Rule Pack Admin API (Foundations)

## Roles & Permissions

- `HR_MANAGER`: create/update draft packs.
- `HR_ADMIN`: publish/retire packs, manage role assignments.

## REST Endpoints (v1)

| Method | Path                                            | Description                                | Roles                    |
| ------ | ----------------------------------------------- | ------------------------------------------ | ------------------------ |
| `POST` | `/internal/hr/policy-packs`                     | Create draft pack.                         | `HR_MANAGER`, `HR_ADMIN` |
| `PUT`  | `/internal/hr/policy-packs/{packCode}/draft`    | Update draft payload.                      | `HR_MANAGER`, `HR_ADMIN` |
| `POST` | `/internal/hr/policy-packs/{packCode}/validate` | Trigger validation run, return findings.   | `HR_MANAGER`, `HR_ADMIN` |
| `POST` | `/internal/hr/policy-packs/{packCode}/publish`  | Publish draft as active version.           | `HR_ADMIN`               |
| `POST` | `/internal/hr/policy-packs/{packCode}/retire`   | Retire active version.                     | `HR_ADMIN`               |
| `GET`  | `/internal/hr/policy-packs`                     | List packs with filters (status, country, region). | `HR_MANAGER`, `HR_ADMIN` |
| `GET`  | `/internal/hr/policy-packs/{packCode}/history`  | Retrieve version history and diffs.        | `HR_MANAGER`, `HR_ADMIN` |
| `GET`  | `/internal/hr/policy-packs/{packCode}/lineage`  | Fetch resolved payload + inheritance chain.| `HR_MANAGER`, `HR_ADMIN` |

## Payload Structure

```json
{
  "packCode": "DEFAULT",
  "countryCode": "US",
  "name": "US Core HR Pack",
  "description": "Baseline policies for US operations",
  "payload": {
    "leave": {...},
    "payroll": {...},
    "compliance": {...}
  }
}
```

## Wireframe Notes

- **Draft Detail View:** JSON editor + validation panel (errors/warnings).
- **Publish Modal:** shows diff vs. current active, requires confirmation comment.
- **History Timeline:** list of versions with status badges, effective dates, author.
- **Hierarchy Sidebar:** breadcrumb renders `GLOBAL-BASE → EU-BASELINE → UK` using lineage endpoint.
- **Region Filter Chips:** UI toggles `regionCode` query param to narrow listing.
- **Diff Viewer:** compares `resolvedPayload` against immediate parent payload highlighting overrides.

## Validation Workflow

1. Draft save triggers schema validation (client + server).
2. Optional sandbox run uses calculators to produce sample outcomes.
3. Publish checks:
   - No active pack overlap.
   - Required sections present (leave, payroll baseline).
   - Checksums computed and stored.
4. Retire requires replacement pack or explicit acceptance of gap.
