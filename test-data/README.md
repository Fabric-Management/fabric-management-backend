# 🧪 Test Data - Company Management Flow

**Purpose:** Test JSON files for company management workflow  
**Last Updated:** 2025-10-09

---

## 📝 Complete Workflow

### Step 1: Create Customer Company

```bash
POST http://localhost:8080/api/v1/companies
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN

Body: @1-create-customer-company.json
```

**Response:**

```json
{
  "success": true,
  "data": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Company created successfully"
}
```

---

### Step 2: Add Email Contact

```bash
POST http://localhost:8080/api/v1/companies/{companyId}/contacts
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN

Body: @2-add-email-contact.json
```

**Response:**

```json
{
  "success": true,
  "data": "660e8400-e29b-41d4-a716-446655440001",
  "message": "Contact added to company successfully"
}
```

---

### Step 3: Add Phone Contact

```bash
POST http://localhost:8080/api/v1/companies/{companyId}/contacts
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN

Body: @3-add-phone-contact.json
```

---

### Step 4: Add CEO User (Ahmet Yılmaz)

```bash
POST http://localhost:8080/api/v1/companies/{companyId}/users
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN

Body: @4-add-ceo-user.json
```

**Response:**

```json
{
  "success": true,
  "message": "User added to company successfully"
}
```

---

### Step 5: Add PURCHASER User (Ayşe Demir)

```bash
POST http://localhost:8080/api/v1/companies/{companyId}/users
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN

Body: @5-add-purchaser-user.json
```

---

## 🎯 Test Endpoints Summary

| Step | Method | Endpoint                                 | Body File                        | Auth Required            |
| ---- | ------ | ---------------------------------------- | -------------------------------- | ------------------------ |
| 1    | POST   | `/api/v1/companies`                      | `1-create-customer-company.json` | ✅ ADMIN/COMPANY_MANAGER |
| 2    | POST   | `/api/v1/companies/{companyId}/contacts` | `2-add-email-contact.json`       | ✅ ADMIN/COMPANY_MANAGER |
| 3    | POST   | `/api/v1/companies/{companyId}/contacts` | `3-add-phone-contact.json`       | ✅ ADMIN/COMPANY_MANAGER |
| 4    | POST   | `/api/v1/companies/{companyId}/users`    | `4-add-ceo-user.json`            | ✅ ADMIN/COMPANY_MANAGER |
| 5    | POST   | `/api/v1/companies/{companyId}/users`    | `5-add-purchaser-user.json`      | ✅ ADMIN/COMPANY_MANAGER |

---

## 🔑 Required Roles

- `ADMIN` - Full access
- `COMPANY_MANAGER` - Company management access

---

**Note:** Replace `{companyId}` with actual UUID from Step 1 response.

