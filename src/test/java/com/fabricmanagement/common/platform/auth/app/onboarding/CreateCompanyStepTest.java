package com.fabricmanagement.common.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateCompanyStep")
class CreateCompanyStepTest {

  @Mock private CompanyFacade companyFacade;

  @InjectMocks private CreateCompanyStep step;

  @Test
  @DisplayName("execute creates tenant company via facade and sets context")
  void execute() {
    UUID companyId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    CompanyDto companyDto =
        CompanyDto.builder()
            .id(companyId)
            .tenantId(tenantId)
            .uid("ACME-001")
            .companyName("Acme Corp")
            .build();
    when(companyFacade.createTenantCompany(any())).thenReturn(companyDto);

    OnboardingContext context = new OnboardingContext();
    context.setCompanyName("Acme Corp");
    context.setTaxId("123");
    context.setCompanyType(CompanyType.SPINNER);

    step.execute(context);

    verify(companyFacade).createTenantCompany(any());
    assertThat(context.getCompanyId()).isEqualTo(companyId);
    assertThat(context.getTenantId()).isEqualTo(tenantId);
    assertThat(context.getCompanyUid()).isEqualTo("ACME-001");
    assertThat(context.getCompanyName()).isEqualTo("Acme Corp");
  }
}
