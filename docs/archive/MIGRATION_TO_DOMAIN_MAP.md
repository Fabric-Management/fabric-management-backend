# Migration to Domain Map (V*.sql only)

Analysis of all versioned migrations under `src/main/resources/db/migration/`. Only `V*.sql` files; repeatable migrations `R__*.sql` are excluded.

---

## MIGRATION_TO_DOMAIN_MAP

One line per file (file → DOMAIN: objects):

- V001 → COMMON: schemas (common_company, common_user, common_auth, common_policy, common_audit, common_communication), sequences, event_publication
- V002 → COMMON: common_company.common_company, common_os_definition, common_subscription, common_feature_catalog, common_subscription_quota
- V003 → COMMON: common_user.common_user
- V005 → COMMON: common_audit.common_audit_log
- V006 → COMMON: common_auth_user, common_refresh_token, common_verification_code, common_registration_token
- V007 → COMMON: common_policy.common_policy
- V008 → FIBER: production schema, prod_fiber_category, prod_fiber_attribute, prod_fiber_certification, prod_fiber_iso_code; INSERT (move to SEEDS)
- V009 → FIBER: prod_material, prod_fiber, prod_fiber_composition, prod_fiber_attribute_link, prod_fiber_certification_link
- V010 → FIBER/IWM: production_execution_fiber_batch (→ production_execution_batch); INSERT moved to R__001
- V012 → YARN: prod_yarn_category, prod_yarn_attribute, prod_yarn_certification; INSERT (move to SEEDS)
- V013 → COMMON: common_department_category, common_role, common_department, common_user_department
- V015 → COMMON: common_ai schema, common_ai_log
- V016 → COMMON: common_contact, common_address, common_user_contact, common_company_contact, common_user_address, common_company_address
- V017 → COMMON/SEED: INSERT platform company, admin user, departments (move to SEEDS)
- V019 → HR: common_user.profile_update_request
- V021 → HR: human schema, human_employee
- V022 → COMMON: common_position, common_user_position (both dropped later)
- V023 → HR: ALTER human_employee (compliance fields)
- V024 → HR: human_employee_number_sequence
- V025 → FIBER: ALTER prod_fiber, DROP prod_fiber_composition, production_quality_fiber_test_result
- V026 → NOTIFICATION: communication_email_outbox
- V029 → HR: human_hr_policy_pack
- V030 → HR: human_hr_rule_version, human_hr_policy_binding, human_hr_rule_audit_log
- V031 → HR: human_leave_type, human_leave_balance, human_leave_accrual_log, human_holiday_calendar
- V032 → HR: human_pay_period, human_pay_run, human_pay_run_entry, human_pay_run_payout, human_pay_run_audit_log
- V033 → HR: human_hr_country_pack_mapping, ALTER human_hr_policy_pack; INSERT (move to SEEDS)
- V034 → COMMON: common_address_contact (dropped in V036)
- V035 → COMMON: uk_company_tenant_tax_id on common_company
- V036 → COMMON: junction tables in common_company/common_user, ALTER address/contact
- V037 → COMMON: common_user.common_role, DROP common_company.common_role
- V038 → COMMON: DROP common_auth_user.contact_id
- V039 → TRADING: trading_partner_registry, common_trading_partner
- V040 → TRADING: INSERT from Company (data migration)
- V042 → LOGISTICS: logistics_sales_order (→ order.sales_order in V066)
- V043 → TRADING: finance.finance_invoice
- V044 → LOGISTICS: logistics_shipment
- V045 → TENANT: common_tenant schema, common_tenant.common_tenant
- V046 → COMMON/TENANT: common_company → common_organization, junction renames, INSERT common_tenant
- V047 → COMMON: tenant_id on user junction tables, uk_contact_tenant_value_type
- V048 → TRADING: organization_id on common_trading_partner, organization_certification type
- V049 → NOTIFICATION: verification_log, routing_config, trusted_device; INSERT routing_config
- V050 → COMMON/HR: DROP department_category, position, user_position; common_user_work_location
- V051 → (all): deleted_at on entity tables
- V052 → COMMON/HR: DROP legacy user/employee columns
- V053 → COMMON: primary flags on junctions, DROP is_primary from contact/address
- V054 → COMMON: enrichment columns on organization, address
- V055 → FIBER: fiber test result quality gate columns
- V056 → IWM: production_execution_batch_lineage
- V057 → FIBER: prod_fiber_specification (→ prod_fiber_quality_standard in V082)
- V058 → IWM: waste_quantity on batch, production_execution_inventory_transaction
- V059 → IWM: production_execution_fiber_batch_reservation (→ production_execution_batch_reservation)
- V060 → IWM: production_execution_warehouse_location, location_id on batch
- V061 → IWM: production_execution_fiber_batch → production_execution_batch, batch_reservation rename
- V062 → IWM: warehouse location type MACHINE/PRODUCTION_LINE
- V063 → FIBER: fiber_batch_id → batch_id on fiber_test_result
- V064 → IWM: inventory_transaction columns, production_execution_inventory_balance
- V065 → IWM: warehouse_location enterprise columns
- V066 → TRADING: order schema, logistics_sales_order → order.sales_order
- V067 → COMMON: registration_token company_id → organization_id
- V068 → IWM: batch status extension, parent_batch_id
- V069 → IWM: production_execution_batch_override_log
- V070 → FIBER: DROP prod_fiber_attribute_link, prod_fiber_certification_link; DROP fiber_grade
- V071 → TRADING: partner_trading_partner_certification
- V072 → COMMON/TRADING: organization_certification
- V073 → IWM/FIBER: production_execution_batch_certification
- V074 → IWM/FIBER: production_execution_batch_attribute
- V075 → FIBER: prod_fiber category/iso_code NOT NULL
- V079 → NOTIFICATION: common_notification
- V080 → IWM/FIBER: production_fiber_request
- V081 → TENANT/SEED: INSERT system tenant
- V082 → FIBER: prod_fiber_quality_standard (DROP prod_fiber_specification)
- V083 → NOTIFICATION: chk_notif_type BATCH_NO_QUALITY_STANDARD
- V084 → IWM/FIBER: batch quality_standard_id

---

## Detailed table (reference)

| Migration | Domain | Objects (schemas, tables, sequences) | INSERTs (candidates for SEEDS) |
|-----------|--------|--------------------------------------|--------------------------------|
| V001__common_schemas_init.sql | COMMON | Schemas: common_company, common_user, common_auth, common_policy, common_audit, common_communication. Sequences: common_company.seq_company, seq_department, seq_subscription; common_user.seq_user; common_auth.seq_verification_code; common_policy.seq_policy; common_audit.seq_audit_log. Table: event_publication (public) + indexes. | — |
| V002__common_company_tables.sql | COMMON | common_company.common_company, common_company.common_os_definition, common_company.common_subscription, common_company.common_feature_catalog, common_company.common_subscription_quota + indexes. | — |
| V003__common_user_tables.sql | COMMON | common_user.common_user + indexes. | — |
| V005__common_audit_tables.sql | COMMON | common_audit.common_audit_log + indexes. | — |
| V006__common_auth_tables.sql | COMMON | common_auth.common_auth_user, common_auth.common_refresh_token, common_auth.common_verification_code, common_auth.common_registration_token + indexes. | — |
| V007__common_policy_tables.sql | COMMON | common_policy.common_policy + indexes. | — |
| V008__fiber_reference_tables.sql | FIBER | Schema: production. Tables: production.prod_fiber_category, prod_fiber_attribute, prod_fiber_certification, prod_fiber_iso_code + indexes. | INSERT prod_fiber_category (8), prod_fiber_attribute (20), prod_fiber_certification (12), prod_fiber_iso_code (52). |
| V009__production_fiber_and_composition.sql | FIBER | production.prod_material, production.prod_fiber, production.prod_fiber_composition (dropped in V025), production.prod_fiber_attribute_link (dropped in V070), production.prod_fiber_certification_link (dropped in V070) + indexes. | — |
| V010__seed_system_100_pure_fibers.sql | FIBER / IWM | production.production_execution_fiber_batch → production.production_execution_batch (V061) + indexes. | Note: seed data moved to R__001__fiber_seeds.sql. |
| V012__yarn_reference_tables.sql | YARN | production.prod_yarn_category, prod_yarn_attribute, prod_yarn_certification + indexes. | INSERT prod_yarn_category (5), prod_yarn_attribute (6), prod_yarn_certification (4). |
| V013__role_department_architecture.sql | COMMON / HR | common_company.common_department_category (dropped V050), common_company.common_role (moved to common_user in V037), common_company.common_department, common_user.common_user_department + indexes. | — |
| V015__ai_audit_table.sql | COMMON | Schema: common_ai. common_ai.common_ai_log + indexes. | — |
| V016__contact_address_tables.sql | COMMON | common_communication schema/sequences, common_contact, common_address, common_user_contact (moved to common_user in V036), common_company_contact (moved to common_company in V036), common_user_address (moved in V036), common_company_address (moved in V036) + indexes; V016 also ALTER common_address (country_code, district, lat/long, place_id, formatted_address). | — |
| V017__seed_platform_system_company_and_admin.sql | COMMON / SEED | No new tables. | INSERT common_company (Platform System), common_user, common_contact, common_user_contact, common_role (if missing), common_department (18 platform-level), UPDATE user role. |
| V019__profile_update_request_table.sql | HR | common_user.profile_update_request + indexes. | — |
| V021__human_employee_module.sql | HR | Schema: human. human.human_employee + indexes. | — |
| V022__position_architecture.sql | COMMON | common_company.common_position (dropped V050), common_user.common_user_position (dropped V050) + indexes. | — |
| V023__add_hr_compliance_tracking.sql | HR | ALTER human.human_employee (hr_compliance_status, missing_fields, last_compliance_check_at) + indexes. | — |
| V024__employee_number_sequence_table.sql | HR | human.human_employee_number_sequence + index. | — |
| V025__fiber_refactoring.sql | FIBER | ALTER prod_fiber (composition JSONB, status constraint, drop measurement columns); DROP prod_fiber_composition; production.production_quality_fiber_test_result + indexes. | — |
| V026__email_outbox_table.sql | NOTIFICATION | common_communication.communication_email_outbox + indexes. | — |
| V029__human_hr_policy_pack.sql | HR | human.human_hr_policy_pack + indexes, trigger. | — |
| V030__hr_policy_pack_extensions.sql | HR | human.human_hr_rule_version, human.human_hr_policy_binding, human.human_hr_rule_audit_log + indexes. ALTER human_hr_policy_pack. | — |
| V031__leave_domain_persistence.sql | HR | human.human_leave_type, human_leave_balance, human_leave_accrual_log, human.human_holiday_calendar + indexes. | — |
| V032__payroll_domain_core.sql | HR | human.human_pay_period, human_pay_run, human_pay_run_entry, human_pay_run_payout, human_pay_run_audit_log + indexes. | — |
| V033__hr_policy_pack_hierarchy.sql | HR | ALTER human_hr_policy_pack (parent_pack_id, region_code, inheritance_mode); human.human_hr_country_pack_mapping + index. | INSERT human_hr_policy_pack (GLOBAL-BASE, EU-BASELINE). |
| V034__address_contact_junction.sql | COMMON | common_communication.common_address_contact (dropped in V036). | — |
| V035__company_tax_id_tenant_scoped_unique.sql | COMMON | ALTER common_company.common_company (uk_company_tenant_tax_id). | — |
| V036__communication_refactor_junction_schemas.sql | COMMON | DROP junction from common_communication; CREATE common_company.common_company_contact, common_company.common_company_address; common_user.common_user_contact, common_user.common_user_address; ALTER common_address (contact_phone, contact_email, contact_person); ALTER common_contact (drop is_whatsapp); ALTER chk_address_type. | — |
| V037__role_move_to_common_user.sql | COMMON | common_user.common_role + indexes; INSERT from common_company.common_role; DROP common_company.common_role; FK updates. | — |
| V038__auth_user_drop_contact_id.sql | COMMON | ALTER common_auth.common_auth_user (DROP contact_id). | — |
| V039__create_trading_partner_tables.sql | TRADING | common_company.trading_partner_registry, common_company.common_trading_partner + indexes; VIEW v_partner_legacy_mapping. | — |
| V040__migrate_company_to_trading_partner.sql | TRADING | Data migration (no new tables). | INSERT trading_partner_registry, common_trading_partner from Company. |
| V042__create_logistics_order_tables.sql | LOGISTICS | Schema: logistics. logistics.logistics_sales_order (moved to "order".sales_order in V066) + indexes. | — |
| V043__create_finance_invoice_tables.sql | TRADING | Schema: finance. finance.finance_invoice + indexes. | — |
| V044__create_logistics_shipment_tables.sql | LOGISTICS | logistics.logistics_shipment + indexes. | — |
| V045__create_tenant_table.sql | TENANT | Schema: common_tenant. common_tenant.common_tenant + indexes. | — |
| V046__migrate_company_to_tenant_and_organization.sql | COMMON / TENANT | INSERT common_tenant from root companies; ALTER common_company.common_company (fk_organization_tenant); RENAME common_company → common_organization, company_* → organization_*, company_id → organization_id; common_company_contact → common_organization_contact, common_company_address → common_organization_address; User.company_id → organization_id; department company_id → organization_id; FKs to common_tenant. | INSERT common_tenant. |
| V047__add_tenant_id_to_user_junction_tables.sql | COMMON | ALTER common_user_department, common_user_position (tenant_id, audit); ALTER common_registration_token (token_type); uk_contact_tenant_value_type on common_contact. | — |
| V048__add_organization_id_to_trading_partner.sql | TRADING | ALTER common_trading_partner (organization_id); ALTER common_organization (chk organization_type EXTERNAL_PARTNER); backfill. | — |
| V049__auth_mfa_trusted_device_and_routing.sql | NOTIFICATION / COMMON | ALTER common_auth_user (MFA columns); common_communication.common_verification_log, common_routing_config + indexes; common_auth.common_trusted_device + indexes; ALTER common_refresh_token (device columns). | INSERT common_routing_config (TR, GB). |
| V050__user_role_department_cleanup.sql | COMMON / HR | DROP common_department_category, common_position, common_user_position; ALTER common_department, common_role (drop department_category_id); common_user.common_user_work_location + indexes; role_scope, user_type on role/user. | — |
| V051__add_deleted_at_audit_column.sql | (all) | ADD deleted_at to many entity tables + partial indexes. | — |
| V052__remove_legacy_fields_from_user_and_employee.sql | COMMON / HR | ALTER common_user (DROP department, contact_value, contact_type, display_name); ALTER human_employee (DROP uid). | — |
| V053__migrate_primary_contacts_addresses_and_normalize.sql | COMMON | Migrate is_primary to junction tables; DROP common_contact.is_primary, common_address.is_primary. | — |
| V054__add_enrichment_fields_to_organization_and_address.sql | COMMON | ALTER common_organization (legal_name, registration_number, industry, website, description); ALTER common_address (address_line2). | — |
| V055__fiber_test_result_quality_gate.sql | FIBER | ALTER production_quality_fiber_test_result (moisture_percent, trash_content_percent, approval_status) + index. | — |
| V056__batch_lineage_table.sql | IWM | production.production_execution_batch_lineage + indexes. | — |
| V057__fiber_specification_table.sql | FIBER | production.prod_fiber_specification (dropped in V082) + indexes. | — |
| V058__waste_quantity_and_inventory_transaction.sql | IWM | ALTER production_execution_fiber_batch (waste_quantity, constraints); production.production_execution_inventory_transaction + indexes. | — |
| V059__fiber_batch_reservation_table.sql | IWM | production.production_execution_fiber_batch_reservation → production_execution_batch_reservation (V061) + indexes; ALTER inventory_transaction type. | — |
| V060__warehouse_location_and_split.sql | IWM | production.production_execution_warehouse_location; ALTER production_execution_fiber_batch (location_id, DROP warehouse_location); ALTER inventory_transaction (location_id, type). | — |
| V061__batch_generalization.sql | IWM | RENAME production_execution_fiber_batch → production_execution_batch, fiber_id → material_id; material_type, attributes; RENAME production_execution_fiber_batch_reservation → production_execution_batch_reservation; index/constraint renames. | — |
| V062__wip_machine_location_type.sql | IWM | ALTER production_execution_warehouse_location (type MACHINE, PRODUCTION_LINE); ALTER inventory_transaction (type, location_id). | — |
| V063__fiber_test_result_batch_id.sql | FIBER | ALTER production_quality_fiber_test_result RENAME fiber_batch_id → batch_id. | — |
| V064__enterprise_inventory_transaction.sql | IWM | ALTER production_execution_inventory_transaction (reason_code, idempotency_key, allow negative qty); production.production_execution_inventory_balance + indexes. | — |
| V065__warehouse_location_enterprise_upgrade.sql | IWM | ALTER production_execution_warehouse_location (description, status, storage_condition, path, level, sort_order, barcode, address_id, capacity, linked_machine_id) + constraints/indexes. | — |
| V066__order_schema_sales_order.sql | TRADING | Schema "order"; ALTER TABLE logistics.logistics_sales_order SET SCHEMA "order"; RENAME to sales_order. | — |
| V067__rename_company_id_to_organization_id.sql | COMMON | ALTER common_auth.common_registration_token company_id → organization_id. | — |
| V068__batch_status_extension.sql | IWM | ALTER production_execution_batch (status values, parent_batch_id) + indexes. | — |
| V069__batch_override_log.sql | IWM | production.production_execution_batch_override_log + indexes. | — |
| V070__fiber_catalog_simplification.sql | FIBER | DROP prod_fiber_attribute_link, prod_fiber_certification_link; ALTER prod_fiber (DROP fiber_grade). | — |
| V071__partner_trading_partner_certification.sql | TRADING | common_company.partner_trading_partner_certification + indexes. | — |
| V072__organization_certification.sql | COMMON / TRADING | common_company.organization_certification + indexes. | — |
| V073__production_execution_batch_certification.sql | IWM / FIBER | production.production_execution_batch_certification + indexes. | — |
| V074__production_execution_batch_attribute.sql | IWM / FIBER | production.production_execution_batch_attribute + indexes. | — |
| V075__fiber_category_iso_code_not_null.sql | FIBER | ALTER prod_fiber (fiber_category_id, fiber_iso_code_id SET NOT NULL). | — |
| V079__common_notification.sql | NOTIFICATION | common_communication.common_notification + indexes. | — |
| V080__production_fiber_request.sql | IWM / FIBER | production.production_fiber_request + indexes. | — |
| V081__insert_system_tenant.sql | TENANT / SEED | No new tables. | INSERT common_tenant (SYSTEM_TENANT_ID). |
| V082__prod_fiber_quality_standard.sql | FIBER | DROP prod_fiber_specification; production.prod_fiber_quality_standard + indexes/constraints. | — |
| V083__notification_type_batch_no_quality_standard.sql | NOTIFICATION | ALTER common_notification (chk_notif_type add BATCH_NO_QUALITY_STANDARD). | — |
| V084__batch_quality_standard.sql | IWM / FIBER | ALTER production_execution_batch (quality_standard_id FK) + index. | — |

---

## TABLES_BY_DOMAIN

Final table names (schema.table) per domain, inferred from full migration history (including renames and drops).

### COMMON

- **common_company:** common_company.common_organization, common_company.common_os_definition, common_company.common_subscription, common_company.common_feature_catalog, common_company.common_subscription_quota, common_company.common_department, common_company.common_organization_contact, common_company.common_organization_address, common_company.trading_partner_registry, common_company.common_trading_partner, common_company.partner_trading_partner_certification, common_company.organization_certification
- **common_user:** common_user.common_user, common_user.common_role, common_user.profile_update_request, common_user.common_user_department, common_user.common_user_contact, common_user.common_user_address, common_user.common_user_work_location
- **common_auth:** common_auth.common_auth_user, common_auth.common_refresh_token, common_auth.common_verification_code, common_auth.common_registration_token, common_auth.common_trusted_device
- **common_audit:** common_audit.common_audit_log
- **common_policy:** common_policy.common_policy
- **common_communication:** common_communication.common_contact, common_communication.common_address, common_communication.common_verification_log, common_communication.common_routing_config, common_communication.communication_email_outbox, common_communication.common_notification
- **common_ai:** common_ai.common_ai_log
- **public:** event_publication

### FIBER

- production.prod_fiber_category  
- production.prod_fiber_attribute  
- production.prod_fiber_certification  
- production.prod_fiber_iso_code  
- production.prod_fiber  
- production.production_quality_fiber_test_result  
- production.prod_fiber_quality_standard  
- production.production_fiber_request  

### YARN

- production.prod_yarn_category  
- production.prod_yarn_attribute  
- production.prod_yarn_certification  

### HR

- human.human_employee  
- human.human_employee_number_sequence  
- human.human_hr_policy_pack  
- human.human_hr_rule_version  
- human.human_hr_policy_binding  
- human.human_hr_rule_audit_log  
- human.human_hr_country_pack_mapping  
- human.human_leave_type  
- human.human_leave_balance  
- human.human_leave_accrual_log  
- human.human_holiday_calendar  
- human.human_pay_period  
- human.human_pay_run  
- human.human_pay_run_entry  
- human.human_pay_run_payout  
- human.human_pay_run_audit_log  
- common_user.profile_update_request  

### TRADING

- common_company.trading_partner_registry  
- common_company.common_trading_partner  
- common_company.partner_trading_partner_certification  
- common_company.organization_certification  
- finance.finance_invoice  
- "order".sales_order  

### LOGISTICS

- logistics.logistics_shipment  

### IWM (Inventory / Warehouse / Batch execution)

- production.prod_material  
- production.production_execution_batch  
- production.production_execution_batch_reservation  
- production.production_execution_batch_lineage  
- production.production_execution_inventory_transaction  
- production.production_execution_inventory_balance  
- production.production_execution_warehouse_location  
- production.production_execution_batch_override_log  
- production.production_execution_batch_certification  
- production.production_execution_batch_attribute  
- production.production_fiber_request  

### TENANT

- common_tenant.common_tenant  

### NOTIFICATION

- common_communication.common_notification  
- common_communication.communication_email_outbox  
- common_communication.common_verification_log  
- common_communication.common_routing_config  
- common_auth.common_trusted_device  

### SEED (migrations that contain INSERT; move to SEEDS / V010__SEEDS later)

- **V008:** prod_fiber_category, prod_fiber_attribute, prod_fiber_certification, prod_fiber_iso_code  
- **V010:** execution fiber batch table only (seed content in R__001__fiber_seeds.sql)  
- **V012:** prod_yarn_category, prod_yarn_attribute, prod_yarn_certification  
- **V017:** common_company (Platform System), common_user, common_contact, common_user_contact, common_role, common_department  
- **V033:** human_hr_policy_pack (GLOBAL-BASE, EU-BASELINE)  
- **V049:** common_routing_config (TR, GB)  
- **V081:** common_tenant (system tenant)  

---

*Generated from analysis of all V*.sql files under `src/main/resources/db/migration/`.*
