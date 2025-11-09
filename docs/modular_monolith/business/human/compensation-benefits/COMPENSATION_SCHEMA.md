# Compensation & Benefits Essentials

## Salary Bands
- Fields: `band_code`, `currency`, `min_amount`, `mid_amount`, `max_amount`, `effective_from`, `effective_to`
- Linked to job families/departments

## Compensation Cycles
- Track cycle metadata: `cycle_code`, `fiscal_year`, `status`, `lock_date`
- Store recommended vs. approved increases per employee

## Benefit Enrollments
- Fields: `enrollment_id`, `employee_id`, `benefit_plan_code`, `coverage_level`, `effective_from`, `effective_to`
- Eligibility rules reference policy packs (country-specific)

## Audit Requirements
- Log every adjustment with actor, reason, before/after amounts
- Approval chain (manager → HR → finance) captured in workflow table

