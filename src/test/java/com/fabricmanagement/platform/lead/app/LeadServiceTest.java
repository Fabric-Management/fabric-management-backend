package com.fabricmanagement.platform.lead.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.lead.domain.Lead;
import com.fabricmanagement.platform.lead.dto.LeadDto;
import com.fabricmanagement.platform.lead.infra.repository.LeadRepository;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LeadServiceTest {

  private final LeadRepository leadRepository = Mockito.mock(LeadRepository.class);
  private final LeadService leadService = new LeadService(leadRepository);

  @Test
  void captureFromSignupMapsAllSignupFields() {
    UUID tenantId = UUID.randomUUID();
    when(leadRepository.save(Mockito.any(Lead.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    LeadDto result =
        leadService.captureFromSignup(
            "Acme Textiles",
            "ACME-123",
            OrganizationType.VERTICAL_MILL,
            "Ada",
            "Lovelace",
            "ada@example.com",
            List.of("FabricOS", "WarehouseOS"),
            "PLAYGROUND",
            tenantId);

    ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);
    Mockito.verify(leadRepository).save(leadCaptor.capture());
    Lead lead = leadCaptor.getValue();
    assertThat(lead.getCompanyName()).isEqualTo("Acme Textiles");
    assertThat(lead.getTaxId()).isEqualTo("ACME-123");
    assertThat(lead.getOrganizationType()).isEqualTo(OrganizationType.VERTICAL_MILL);
    assertThat(lead.getFirstName()).isEqualTo("Ada");
    assertThat(lead.getLastName()).isEqualTo("Lovelace");
    assertThat(lead.getWorkEmail()).isEqualTo("ada@example.com");
    assertThat(lead.getSelectedOs()).containsExactly("FabricOS", "WarehouseOS");
    assertThat(lead.getSignupIntent()).isEqualTo("PLAYGROUND");
    assertThat(lead.getTrialTenantId()).isEqualTo(tenantId);

    assertThat(result.companyName()).isEqualTo("Acme Textiles");
    assertThat(result.selectedOs()).containsExactly("FabricOS", "WarehouseOS");
    assertThat(result.signupIntent()).isEqualTo("PLAYGROUND");
    assertThat(result.trialTenantId()).isEqualTo(tenantId);
  }
}
