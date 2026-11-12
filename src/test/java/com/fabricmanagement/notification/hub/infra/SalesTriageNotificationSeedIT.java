package com.fabricmanagement.notification.hub.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.domain.SystemDepartment;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SalesTriageNotificationSeedIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void goldenTemplateContainsTheSalesTriageTemplateAndTranslations() {
    Integer templates =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM notification.notification_template
            WHERE tenant_id = ?
              AND event_type = 'CUSTOMER_OWNERSHIP_TRIAGE_OPENED'
              AND channel = 'IN_APP'
              AND title_key = 'notification.customer_ownership_triage_opened.title'
              AND body_key = 'notification.customer_ownership_triage_opened.body'
            """,
            Integer.class,
            TenantContext.TEMPLATE_TENANT_ID);
    Integer keys =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM i18n.translation_key
            WHERE tenant_id = ?
              AND key_code IN (
                'notification.customer_ownership_triage_opened.title',
                'notification.customer_ownership_triage_opened.body'
              )
            """,
            Integer.class,
            TenantContext.TEMPLATE_TENANT_ID);
    Integer turkishValues =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM i18n.translation_value value
            JOIN i18n.translation_key translation_key
              ON translation_key.id = value.translation_key_id
             AND translation_key.tenant_id = value.tenant_id
            WHERE value.tenant_id = ?
              AND value.locale = 'TR'
              AND translation_key.key_code IN (
                'notification.customer_ownership_triage_opened.title',
                'notification.customer_ownership_triage_opened.body'
              )
            """,
            Integer.class,
            TenantContext.TEMPLATE_TENANT_ID);

    assertThat(templates).isEqualTo(1);
    assertThat(keys).isEqualTo(2);
    assertThat(turkishValues).isEqualTo(2);
  }

  @Test
  void goldenTemplateEscalatesInAppAndOffersEmailAsAnOptionalChannel() {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT channel, importance, title_key, body_key
            FROM notification.notification_template
            WHERE tenant_id = ? AND event_type = 'CUSTOMER_OWNERSHIP_TRIAGE_AGED'
            ORDER BY channel
            """,
            TenantContext.TEMPLATE_TENANT_ID);
    Integer turkishValues =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM i18n.translation_value value
            JOIN i18n.translation_key translation_key
              ON translation_key.id = value.translation_key_id
             AND translation_key.tenant_id = value.tenant_id
            WHERE value.tenant_id = ?
              AND value.locale = 'TR'
              AND translation_key.key_code IN (
                'notification.customer_ownership_triage_aged.title',
                'notification.customer_ownership_triage_aged.body'
              )
            """,
            Integer.class,
            TenantContext.TEMPLATE_TENANT_ID);

    assertThat(rows).extracting(row -> row.get("channel")).containsExactly("EMAIL", "IN_APP");
    // Not CRITICAL: CRITICAL ignores the user's preference, and e-mail must stay optional.
    assertThat(rows).allSatisfy(row -> assertThat(row.get("importance")).isEqualTo("HIGH"));
    assertThat(rows)
        .allSatisfy(
            row ->
                assertThat(row.get("title_key"))
                    .isEqualTo("notification.customer_ownership_triage_aged.title"));
    assertThat(turkishValues).isEqualTo(2);
  }

  @Test
  void operationalTenantSeederIncludesTheCanonicalSalesDepartment() throws Exception {
    String tenantSeedSource =
        Files.readString(
            Path.of(
                "src/main/java/com/fabricmanagement/platform/subscription/app/"
                    + "TenantSeedService.java"));

    assertThat(SystemDepartment.fromCode("SALES")).contains(SystemDepartment.SALES);
    assertThat(tenantSeedSource)
        .contains("createDepartment(organizationId, SystemDepartment.SALES, parent);");
  }

  @Test
  void tenantClonerIncludesNotificationTemplatesAndTranslations() throws Exception {
    String clonerSource =
        Files.readString(
            Path.of(
                "src/main/java/com/fabricmanagement/platform/tenant/app/"
                    + "TenantClonerService.java"));

    assertThat(clonerSource)
        .contains("\"notification.notification_template\"")
        .contains("cloneI18nKeysAndValues(");
  }
}
