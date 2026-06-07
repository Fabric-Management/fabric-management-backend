package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for cloning the Golden Template tenant into an isolated Playground tenant.
 * Uses SystemTransactionExecutor (pure JDBC) to bypass RLS and avoid JPA Context contamination.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantClonerService {

  private final SystemTransactionExecutor systemTransactionExecutor;

  /**
   * Find the TEMPLATE tenant ID. Returns null if no template tenant exists.
   *
   * <p>Uses BYPASSRLS (SystemTransactionExecutor) — safe to call without tenant context.
   */
  public UUID findTemplateTenantId() {
    return systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          var results =
              jdbc.queryForList(
                  "SELECT id FROM common_tenant.common_tenant WHERE slug = 'golden-template' LIMIT 1",
                  UUID.class);
          return results.isEmpty() ? null : results.getFirst();
        });
  }

  /**
   * Clone platform roles from a source tenant (typically TEMPLATE) to a target tenant. Uses
   * BYPASSRLS to read source and write to target — no JPA, no RLS constraints.
   *
   * <p>Idempotent: skips roles that already exist in the target tenant (by role_code).
   *
   * @param sourceTenantId the tenant to copy roles FROM
   * @param targetTenantId the tenant to copy roles TO
   * @return number of roles cloned
   */
  public int cloneRolesToTenant(UUID sourceTenantId, UUID targetTenantId) {
    return systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          int[] count = {0};
          jdbc.query(
              "SELECT id, role_name, role_code, description, role_scope FROM common_user.common_role WHERE tenant_id = ? AND is_active = true",
              rs -> {
                String roleCode = rs.getString("role_code");
                // Idempotent — skip if role_code already exists for target tenant
                Integer existing =
                    jdbc.queryForObject(
                        "SELECT COUNT(*) FROM common_user.common_role WHERE tenant_id = ? AND role_code = ?",
                        Integer.class,
                        targetTenantId,
                        roleCode);
                if (existing != null && existing > 0) {
                  return;
                }
                jdbc.update(
                    "INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code, description, role_scope, is_active, created_at, updated_at, version) "
                        + "VALUES (?, ?, gen_random_uuid()::varchar, ?, ?, ?, ?, true, now(), now(), 0)",
                    UUID.randomUUID(),
                    targetTenantId,
                    rs.getString("role_name"),
                    roleCode,
                    rs.getString("description"),
                    rs.getString("role_scope"));
                count[0]++;
              },
              sourceTenantId);
          return count[0];
        });
  }

  public Tenant cloneTemplateToPlayground() {
    log.info("Starting template clone process for new playground tenant (JDBC BYPASSRLS).");

    return systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          // 1. Find the TEMPLATE tenant ID (Nexus Fabrics for playgrounds)
          UUID templateTenantId =
              jdbc.queryForObject(
                  "SELECT id FROM common_tenant.common_tenant WHERE slug = 'nexus-fabrics' LIMIT 1",
                  UUID.class);

          if (templateTenantId == null) {
            throw new IllegalStateException("No PLAYGROUND TEMPLATE tenant found for cloning.");
          }

          // 2. Create the new PLAYGROUND tenant
          String playgroundSuffix = UUID.randomUUID().toString().substring(0, 8);
          UUID newTenantId = UUID.randomUUID();
          String uid = "PG-" + playgroundSuffix;
          String slug = "playground-" + playgroundSuffix;
          String name = "Playground " + playgroundSuffix;

          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, status, settings, is_active, created_at, updated_at, version) "
                  + "VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, true, now(), now(), 0)",
              newTenantId,
              uid,
              slug,
              name,
              TenantType.PLAYGROUND.name(),
              "ACTIVE");

          log.info("Created new PLAYGROUND tenant: {}", newTenantId);

          // 3. Clone Hierarchical Data (Requires mapping old UUIDs to new UUIDs)

          // 3.1 Organization
          Map<UUID, UUID> orgMap = new HashMap<>();
          jdbc.query(
              "SELECT id, name, organization_type, tax_id, description FROM common_company.common_organization WHERE tenant_id = ? AND is_active = true",
              rs -> {
                UUID oldId = (UUID) rs.getObject("id");
                UUID newId = UUID.randomUUID();
                jdbc.update(
                    "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, organization_type, tax_id, description, is_active, created_at, updated_at, version) "
                        + "VALUES (?, ?, gen_random_uuid()::varchar, ?, ?, ?, ?, true, now(), now(), 0)",
                    newId,
                    newTenantId,
                    rs.getString("name"),
                    rs.getString("organization_type"),
                    rs.getString("tax_id"),
                    rs.getString("description"));
                orgMap.put(oldId, newId);
              },
              templateTenantId);

          // 3.2 Department
          Map<UUID, UUID> deptMap = new HashMap<>();
          jdbc.query(
              "SELECT id, organization_id, department_name, department_code, description, is_system_department, department_group, display_order FROM common_company.common_department WHERE tenant_id = ? AND is_active = true",
              rs -> {
                UUID oldId = (UUID) rs.getObject("id");
                UUID newId = UUID.randomUUID();
                UUID oldOrgId = (UUID) rs.getObject("organization_id");
                jdbc.update(
                    "INSERT INTO common_company.common_department (id, tenant_id, uid, organization_id, department_name, department_code, description, is_system_department, department_group, display_order, is_active, created_at, updated_at, version) "
                        + "VALUES (?, ?, gen_random_uuid()::varchar, ?, ?, ?, ?, ?, ?, ?, true, now(), now(), 0)",
                    newId,
                    newTenantId,
                    orgMap.get(oldOrgId),
                    rs.getString("department_name"),
                    rs.getString("department_code"),
                    rs.getString("description"),
                    rs.getBoolean("is_system_department"),
                    rs.getString("department_group"),
                    rs.getInt("display_order"));
                deptMap.put(oldId, newId);
              },
              templateTenantId);

          // 3.3 Role
          Map<UUID, UUID> roleMap = new HashMap<>();
          jdbc.query(
              "SELECT id, role_name, role_code, description, role_scope FROM common_user.common_role WHERE tenant_id = ? AND is_active = true",
              rs -> {
                UUID oldId = (UUID) rs.getObject("id");
                UUID newId = UUID.randomUUID();
                jdbc.update(
                    "INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code, description, role_scope, is_active, created_at, updated_at, version) "
                        + "VALUES (?, ?, gen_random_uuid()::varchar, ?, ?, ?, ?, true, now(), now(), 0)",
                    newId,
                    newTenantId,
                    rs.getString("role_name"),
                    rs.getString("role_code"),
                    rs.getString("description"),
                    rs.getString("role_scope"));
                roleMap.put(oldId, newId);
              },
              templateTenantId);

          // 3.4 User
          Map<UUID, UUID> userMap = new HashMap<>();
          jdbc.query(
              "SELECT id, organization_id, role_id, first_name, last_name, user_type FROM common_user.common_user WHERE tenant_id = ?",
              rs -> {
                UUID oldId = (UUID) rs.getObject("id");
                UUID newId = UUID.randomUUID();
                UUID oldOrgId = (UUID) rs.getObject("organization_id");
                UUID oldRoleId = (UUID) rs.getObject("role_id");
                jdbc.update(
                    "INSERT INTO common_user.common_user (id, tenant_id, uid, organization_id, role_id, first_name, last_name, user_type, is_active, created_at, updated_at, version) "
                        + "VALUES (?, ?, gen_random_uuid()::varchar, ?, ?, ?, ?, ?, true, now(), now(), 0)",
                    newId,
                    newTenantId,
                    oldOrgId != null ? orgMap.get(oldOrgId) : null,
                    oldRoleId != null ? roleMap.get(oldRoleId) : null,
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("user_type"));
                userMap.put(oldId, newId);
              },
              templateTenantId);

          // 3.5 User Department
          jdbc.query(
              "SELECT user_id, department_id, is_primary FROM common_user.common_user_department WHERE tenant_id = ?",
              rs -> {
                UUID oldUserId = (UUID) rs.getObject("user_id");
                UUID oldDeptId = (UUID) rs.getObject("department_id");
                jdbc.update(
                    "INSERT INTO common_user.common_user_department (tenant_id, user_id, department_id, is_primary, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, now(), now())",
                    newTenantId,
                    userMap.get(oldUserId),
                    deptMap.get(oldDeptId),
                    rs.getBoolean("is_primary"));
              },
              templateTenantId);

          // 3.6 Contact
          Map<UUID, UUID> contactMap = new HashMap<>();
          jdbc.query(
              "SELECT id, contact_value, contact_type, is_verified, label, is_personal FROM common_communication.common_contact WHERE tenant_id = ?",
              rs -> {
                UUID oldId = (UUID) rs.getObject("id");
                UUID newId = UUID.randomUUID();
                jdbc.update(
                    "INSERT INTO common_communication.common_contact (id, tenant_id, uid, contact_value, contact_type, is_verified, label, is_personal, created_at, updated_at, version) "
                        + "VALUES (?, ?, gen_random_uuid()::varchar, ?, ?, ?, ?, ?, now(), now(), 0)",
                    newId,
                    newTenantId,
                    rs.getString("contact_value"),
                    rs.getString("contact_type"),
                    rs.getBoolean("is_verified"),
                    rs.getString("label"),
                    rs.getBoolean("is_personal"));
                contactMap.put(oldId, newId);
              },
              templateTenantId);

          // 3.7 User Contact
          jdbc.query(
              "SELECT user_id, contact_id, is_default FROM common_user.common_user_contact WHERE tenant_id = ?",
              rs -> {
                UUID oldUserId = (UUID) rs.getObject("user_id");
                UUID oldContactId = (UUID) rs.getObject("contact_id");
                jdbc.update(
                    "INSERT INTO common_user.common_user_contact (tenant_id, user_id, contact_id, uid, is_default, created_at, updated_at) "
                        + "VALUES (?, ?, ?, gen_random_uuid()::varchar, ?, now(), now())",
                    newTenantId,
                    userMap.get(oldUserId),
                    contactMap.get(oldContactId),
                    rs.getBoolean("is_default"));
              },
              templateTenantId);

          // 4. Clone Reference Data Tables (No internal hierarchical dependencies)

          // 5. PRODUCTION MASTERDATA
          cloneTableWithoutFKs(
              jdbc,
              "production.prod_fiber_category",
              "uid, category_code, category_name, description, is_active",
              templateTenantId,
              newTenantId);
          cloneTableWithoutFKs(
              jdbc,
              "production.prod_fiber_certification",
              "uid, certification_code, certification_name, certifying_body, description, is_active",
              templateTenantId,
              newTenantId);
          cloneTableWithoutFKs(
              jdbc,
              "production.prod_yarn_category",
              "uid, category_code, category_name, description, is_active",
              templateTenantId,
              newTenantId);
          cloneTableWithoutFKs(
              jdbc,
              "production.prod_yarn_attribute",
              "uid, attribute_code, attribute_name, attribute_type, unit, description, is_active",
              templateTenantId,
              newTenantId);
          cloneTableWithoutFKs(
              jdbc,
              "production.prod_yarn_certification",
              "uid, certification_code, certification_name, certifying_body, description, is_active",
              templateTenantId,
              newTenantId);
          cloneTableWithoutFKs(
              jdbc,
              "production.prod_product_attribute",
              "uid, attribute_code, attribute_name, attribute_group, description, display_order, product_scope, is_active",
              templateTenantId,
              newTenantId);

          cloneTableWithoutFKs(
              jdbc,
              "production.prod_fiber_iso_code",
              "uid, iso_code, fiber_name, fiber_type, description, is_official_iso, display_order, is_active",
              templateTenantId,
              newTenantId);

          // hr_policy_pack has parent_pack_id FK, need special handling!
          cloneHrPolicyPacks(jdbc, templateTenantId, newTenantId);

          cloneTableWithoutFKs(
              jdbc,
              "notification.notification_template",
              "uid, event_type, channel, title_key, body_key, importance, delivery_type, grouping_window_minutes, is_active",
              templateTenantId,
              newTenantId);

          // i18n
          cloneTableWithoutFKs(
              jdbc,
              "i18n.supported_locale",
              "uid, code, name, is_rtl, is_active",
              templateTenantId,
              newTenantId);
          cloneI18nKeysAndValues(jdbc, templateTenantId, newTenantId);

          cloneTableWithoutFKs(
              jdbc,
              "costing.cost_item",
              "uid, code, name, description, scope, module_type, calculation_base, display_order, is_active",
              templateTenantId,
              newTenantId);
          cloneTableWithoutFKs(
              jdbc,
              "common_communication.common_routing_config",
              "uid, country_code, primary_channel, fallback_channel, timeout_seconds, is_active",
              templateTenantId,
              newTenantId);

          log.info("Cloning completed for playground tenant: {}", newTenantId);

          // We can't return the full JPA entity. The caller usually just needs the ID or simple
          // info.
          Tenant tenant = Tenant.create(name, uid, slug, null, TenantType.PLAYGROUND);
          tenant.setId(newTenantId);
          return tenant;
        });
  }

  private void cloneTableWithoutFKs(
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      String tableName,
      String columns,
      UUID templateId,
      UUID newTenantId) {
    String sql =
        String.format(
            "INSERT INTO %s (id, tenant_id, %s, created_at, updated_at, version) "
                + "SELECT gen_random_uuid(), ?, %s, now(), now(), 0 FROM %s WHERE tenant_id = ?",
            tableName, columns, columns, tableName);
    jdbc.update(sql, newTenantId, templateId);
  }

  private void cloneHrPolicyPacks(
      org.springframework.jdbc.core.JdbcTemplate jdbc, UUID templateId, UUID newTenantId) {
    Map<UUID, UUID> packMap = new HashMap<>();
    // Order by parent_pack_id NULLS FIRST to ensure parents are inserted before children
    jdbc.query(
        "SELECT id, uid, parent_pack_id, pack_code, pack_version, name, country_code, status, description, effective_from, effective_to, payload, checksum, parent_pack_code, region_code, inheritance_mode FROM human.human_hr_policy_pack WHERE tenant_id = ? ORDER BY parent_pack_id NULLS FIRST",
        rs -> {
          UUID oldId = (UUID) rs.getObject("id");
          UUID newId = UUID.randomUUID();
          UUID oldParentId = (UUID) rs.getObject("parent_pack_id");
          jdbc.update(
              "INSERT INTO human.human_hr_policy_pack (id, tenant_id, uid, parent_pack_id, pack_code, pack_version, name, country_code, status, description, effective_from, effective_to, payload, checksum, parent_pack_code, region_code, inheritance_mode, is_active, created_at, updated_at, version) "
                  + "VALUES (?, ?, gen_random_uuid()::varchar, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, true, now(), now(), 0)",
              newId,
              newTenantId,
              oldParentId != null ? packMap.get(oldParentId) : null,
              rs.getString("pack_code"),
              rs.getInt("pack_version"),
              rs.getString("name"),
              rs.getString("country_code"),
              rs.getString("status"),
              rs.getString("description"),
              rs.getTimestamp("effective_from"),
              rs.getTimestamp("effective_to"),
              rs.getString("payload"),
              rs.getString("checksum"),
              rs.getString("parent_pack_code"),
              rs.getString("region_code"),
              rs.getString("inheritance_mode"));
          packMap.put(oldId, newId);
        },
        templateId);
  }

  private void cloneI18nKeysAndValues(
      org.springframework.jdbc.core.JdbcTemplate jdbc, UUID templateId, UUID newTenantId) {
    Map<UUID, UUID> keyMap = new HashMap<>();
    jdbc.query(
        "SELECT id, uid, module, key_code, default_value, description FROM i18n.translation_key WHERE tenant_id = ?",
        rs -> {
          UUID oldId = (UUID) rs.getObject("id");
          UUID newId = UUID.randomUUID();
          jdbc.update(
              "INSERT INTO i18n.translation_key (id, tenant_id, uid, module, key_code, default_value, description, created_at, updated_at, version) "
                  + "VALUES (?, ?, gen_random_uuid()::varchar, ?, ?, ?, ?, now(), now(), 0)",
              newId,
              newTenantId,
              rs.getString("module"),
              rs.getString("key_code"),
              rs.getString("default_value"),
              rs.getString("description"));
          keyMap.put(oldId, newId);
        },
        templateId);

    jdbc.query(
        "SELECT id, uid, translation_key_id, locale, value, is_override FROM i18n.translation_value WHERE tenant_id = ?",
        rs -> {
          UUID oldKeyId = (UUID) rs.getObject("translation_key_id");
          jdbc.update(
              "INSERT INTO i18n.translation_value (id, tenant_id, uid, translation_key_id, locale, value, is_override, created_at, updated_at, version) "
                  + "VALUES (gen_random_uuid(), ?, gen_random_uuid()::varchar, ?, ?, ?, ?, now(), now(), 0)",
              newTenantId,
              keyMap.get(oldKeyId),
              rs.getString("locale"),
              rs.getString("value"),
              rs.getBoolean("is_override"));
        },
        templateId);
  }
}
