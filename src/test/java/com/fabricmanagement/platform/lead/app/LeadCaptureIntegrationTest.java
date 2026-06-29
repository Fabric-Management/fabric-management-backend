package com.fabricmanagement.platform.lead.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.lead.domain.Lead;
import com.fabricmanagement.platform.lead.infra.repository.LeadRepository;
import com.fabricmanagement.platform.tenant.app.TenantResetService;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
class LeadCaptureIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LeadRepository leadRepository;
  @Autowired private TenantResetService tenantResetService;

  @MockBean private NotificationService notificationService;
  @MockBean private EmailTemplateRenderer emailTemplateRenderer;

  @Test
  void signupCreatesLeadThatSurvivesDemoResetAndHasNoTenantIdColumn() throws Exception {
    stubEmailRendering();
    long suffix = System.currentTimeMillis();
    String organizationName = "Lead Capture Company " + suffix;
    String email = "lead-capture-" + suffix + "@example.com";
    String taxId = "LEAD" + suffix % 100000000;
    String body =
        """
        {
          "organizationName": "%s",
          "taxId": "%s",
          "organizationType": "VERTICAL_MILL",
          "firstName": "Lead",
          "lastName": "Owner",
          "email": "%s",
          "selectedOS": ["FabricOS", "WarehouseOS"],
          "intent": "PLAYGROUND",
          "acceptedTerms": true
        }
        """
            .formatted(organizationName, taxId, email);

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    UUID tenantId =
        jdbc.queryForObject(
            "SELECT id FROM common_tenant.common_tenant WHERE name = ?",
            UUID.class,
            organizationName);
    UUID ownerId = findUserIdByContact(email);

    List<Lead> leadsByEmail = leadRepository.findByWorkEmail(email);
    assertThat(leadsByEmail).hasSize(1);
    Lead lead = leadsByEmail.get(0);
    assertThat(lead.getCompanyName()).isEqualTo(organizationName);
    assertThat(lead.getTaxId()).isEqualTo(taxId);
    assertThat(lead.getFirstName()).isEqualTo("Lead");
    assertThat(lead.getLastName()).isEqualTo("Owner");
    assertThat(lead.getSelectedOs()).containsExactly("FabricOS", "WarehouseOS");
    assertThat(lead.getSignupIntent()).isEqualTo("PLAYGROUND");
    assertThat(lead.getTrialTenantId()).isEqualTo(tenantId);
    assertThat(leadRepository.findByTrialTenantId(tenantId))
        .extracting(Lead::getId)
        .contains(lead.getId());

    assertThat(commonLeadTenantIdColumnCount()).isZero();
    assertThat(commonLeadRlsEnabled()).isFalse();
    assertThat(commonLeadRlsPolicyCount()).isZero();

    tenantResetService.reset(
        new AuthenticatedUserContext(ownerId, "ADMIN", List.of(), null, tenantId));

    assertThat(leadRepository.findByWorkEmail(email))
        .extracting(Lead::getId)
        .containsExactly(lead.getId());
    assertThat(leadRepository.findByTrialTenantId(tenantId))
        .extracting(Lead::getId)
        .contains(lead.getId());
  }

  private void stubEmailRendering() {
    doNothing()
        .when(notificationService)
        .sendNotificationSync(anyString(), anyString(), anyString());
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Setup password email body");
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Welcome email body");
  }

  private UUID findUserIdByContact(String contactValue) {
    return jdbc.queryForObject(
        """
        SELECT u.id
        FROM common_user.common_user u
        JOIN common_user.common_user_contact uc ON uc.user_id = u.id
        JOIN common_communication.common_contact c ON c.id = uc.contact_id
        WHERE lower(c.contact_value) = lower(?)
        """,
        UUID.class,
        contactValue);
  }

  private Integer commonLeadTenantIdColumnCount() {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM information_schema.columns
        WHERE table_schema = 'common_company'
          AND table_name = 'common_lead'
          AND column_name = 'tenant_id'
        """,
        Integer.class);
  }

  private Integer commonLeadRlsPolicyCount() {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM pg_policies
        WHERE schemaname = 'common_company'
          AND tablename = 'common_lead'
        """,
        Integer.class);
  }

  private Boolean commonLeadRlsEnabled() {
    return jdbc.queryForObject(
        """
        SELECT c.relrowsecurity
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'common_company'
          AND c.relname = 'common_lead'
        """,
        Boolean.class);
  }
}
