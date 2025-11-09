# HR Data Privacy & Retention

## PII Inventory
- Employee personal data: `firstName`, `lastName`, `birthDate`, `nationality`, `contactValue`
- Emergency contact details: `name`, `phone`, `relationship`
- Compensation data: salary amounts, benefit selections

## Masking Rules
- Logs store masked `contactValue` (`jo***@example.com`)
- Analytics exports omit birth dates; use age bands instead
- Emergency contact numbers masked except last 2 digits

## Retention Windows
- Employee records: retained while active + 7 years
- Leave requests: 5 years
- Payroll runs: 10 years (financial compliance)
- Rule pack audit logs: 7 years

## Data Subject Requests
- Right-to-erasure triggers anonymization pipeline (replace PII with tokens)
- Export requests produce encrypted ZIP with audit trail
- Audit logs retain compliance with hashed identifiers

