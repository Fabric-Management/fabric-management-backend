# NAVPREF – API Contract & Shared DTO Shape

**Task:** NAVPREF-1 (decision only, no code).  
**Purpose:** Agreed request/response structure for Navigation Preferences. Backend and frontend implement against this contract.

---

## Endpoints (reference)

- **GET** `/api/common/users/{id}/nav-preferences` — load preferences (e.g. after login, to hydrate Zustand).
- **PATCH** `/api/common/users/{id}/nav-preferences` — update preferences (e.g. after reorder/hide, debounced ~500ms).

Security: `id` in path must equal the authenticated user’s ID (JWT). Otherwise **403**.

---

## GET response

**HTTP:** `200 OK` always (see “New user” below).  
**Body:** Wrapped in standard `ApiResponse<T>`, so the payload is in `data`:

```json
{
  "success": true,
  "data": {
    "sortOrder": ["id1", "id2", "id3"],
    "hiddenItemIds": ["id4", "id5"]
  },
  "timestamp": "..."
}
```

| Field          | Type     | Description                                      |
|----------------|----------|--------------------------------------------------|
| `sortOrder`    | string[] | Ordered list of nav item IDs (visible, in order).|
| `hiddenItemIds`| string[] | Nav item IDs that are hidden.                    |

- Both arrays are always present (never `null`). Use `[]` when empty.

---

## PATCH body

**Request body:** Partial update. Only send fields that changed.

```json
{
  "sortOrder": ["id1", "id2", "id3"]
}
```

or

```json
{
  "hiddenItemIds": ["id4", "id5"]
}
```

or both:

```json
{
  "sortOrder": ["id1", "id2"],
  "hiddenItemIds": ["id3"]
}
```

| Field           | Type      | Required in PATCH | Description |
|-----------------|-----------|-------------------|-------------|
| `sortOrder`     | string[]? | No (optional)      | If present, replaces current sort order. |
| `hiddenItemIds` | string[]? | No (optional)     | If present, replaces current hidden list. |

- **Both fields are optional** in PATCH. Omitted fields are left unchanged.
- Client sends only the field(s) that changed.

**Response:** `200 OK` with full preferences in `data` (same shape as GET), or standard `ApiResponse` error on validation/403.

---

## New user (no stored preferences yet)

- **GET** must **never** return `404` for a valid authenticated user.
- If no row exists for that user: return **200** with a **default** body:
  - `sortOrder`: `[]` or the system default order (e.g. all known nav item IDs in a defined order).
  - `hiddenItemIds`: `[]`.

So the client always gets `200` + the same DTO shape and does not need a separate “no preferences” path.

---

## TypeScript (frontend reference)

```ts
// GET response / PATCH response data
interface NavPreferencesDto {
  sortOrder: string[];
  hiddenItemIds: string[];
}

// PATCH request body (all fields optional)
interface NavPreferencesPatchRequest {
  sortOrder?: string[];
  hiddenItemIds?: string[];
}
```

---

## Summary

| Item              | Decision |
|-------------------|----------|
| GET response      | `{ sortOrder: string[], hiddenItemIds: string[] }` (in `ApiResponse.data`) |
| PATCH body        | `{ sortOrder?: string[], hiddenItemIds?: string[] }` — only changed field(s) sent |
| New user          | GET returns **200** + default body; **never 404** |

Implement backend (NAVPREF-2+) and frontend against this contract.
