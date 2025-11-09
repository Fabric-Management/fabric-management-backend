# HR Canonical Schema Reference v1

## Entities
- **Employee (`human_employee`)**
  - Keys: `id (UUID)`, `tenant_id`, `user_id`, `employee_number`
  - Attributes: `title`, `gender`, `birth_date`, `nationality`, `hire_date`, `termination_date`, `hr_compliance_status`, `missing_fields`, `last_compliance_check_at`
- **EmployeeNumberSequence (`human_employee_number_sequence`)**
  - Keys: `tenant_id`
  - Attributes: `next_sequence`, `updated_at`
- **HrPolicyPack (`human_hr_policy_pack`)**
  - Keys: `id`
  - Business Keys: `(tenant_id, pack_code, pack_version)`
  - Attributes: `country_code`, `name`, `description`, `status`, `effective_from`, `effective_to`, `payload`, `checksum`
- **HrRuleVersion (`human_hr_rule_version`)**
  - Keys: `id`
  - Attributes: `policy_pack_id`, `rule_type`, `payload`, `payload_hash`
- **HrPolicyBinding (`human_hr_policy_binding`)**
  - Keys: `id`
  - Attributes: `policy_pack_id`, `policy_interface`, `strategy_bean`, `config_reference`
- **HrRuleAuditLog (`human_hr_rule_audit_log`)**
  - Keys: `id`
  - Attributes: `policy_pack_id`, `pack_code`, `country_code`, `pack_version`, `action`, `actor_id`, `payload_checksum`, `diff_snapshot`, `occurred_at`
- **LeaveType (`human_leave_type`)**
  - Keys: `id`
  - Business Keys: `(tenant_id, code)`
  - Attributes: `name`, `country_code`, `statutory`, `accrual_strategy`, `default_accrual_rate`, `max_carry_over`, `attributes`, `is_active`
- **LeaveBalance (`human_leave_balance`)**
  - Keys: `id`
  - Business Keys: `(tenant_id, employee_id, leave_type_id)`
  - Attributes: `balance_days`, `carry_over_days`, `pending_days`, `last_accrual_at`, `policy_pack_code`, `policy_pack_version`, `country_code`
- **LeaveAccrualLog (`human_leave_accrual_log`)**
  - Keys: `id`
  - Attributes: `employee_id`, `leave_type_id`, `accrual_amount`, `balance_after`, `policy_pack_code`, `policy_pack_version`, `occurred_at`, `context`
- **HolidayCalendar (`human_holiday_calendar`)**
  - Keys: `id`
  - Business Keys: `(tenant_id, country_code, calendar_year)`
  - Attributes: `entries`, `version_tag`

## Value Objects
- `EmergencyContact` → embedded in `Employee` (`name`, `phone`, `relationship`)
- `LeaveAccrualResult` → in-memory calculation result (`leaveTypeCode`, `accruedAmount`, `newBalance`, `calculatedAt`, `policyPackCode`, `policyPackVersion`, `metadata`)
- `LeavePolicyRequest` → in-memory command object (`tenantId`, `employeeId`, `leaveTypeCode`, `leaveTypeId`, `asOfDate`, `currentBalance`, `accrualRatePerPeriod`, `carryOverBalance`, `maxCarryOver`, `lastAccrualAt`, `employmentStartDate`, `policyAttributes`, `policyPackCode`, `policyPackVersion`, `countryCode`)

## Enumerations
- `HrComplianceStatus` → `COMPLETE`, `MISSING_RECOMMENDED`, `MISSING_REQUIRED`
- `EmploymentType`
- `Gender`
- `Title`
- `HrPolicyPackStatus`

## Conventions
- All identifiers are UUID unless explicitly stated (business keys use strings).
- `tenant_id` is mandatory for multi-tenancy isolation.
- `effective_from`/`effective_to` store `TIMESTAMPTZ` in UTC.
- JSON payloads stored using PostgreSQL `jsonb` for rule packs, leave attributes, accrual context, and holiday calendars.

