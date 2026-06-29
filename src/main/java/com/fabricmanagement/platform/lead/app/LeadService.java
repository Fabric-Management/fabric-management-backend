package com.fabricmanagement.platform.lead.app;

import com.fabricmanagement.platform.lead.domain.Lead;
import com.fabricmanagement.platform.lead.dto.LeadDto;
import com.fabricmanagement.platform.lead.infra.repository.LeadRepository;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeadService {

  private final LeadRepository leadRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public LeadDto captureFromSignup(
      String companyName,
      String taxId,
      OrganizationType organizationType,
      String firstName,
      String lastName,
      String workEmail,
      List<String> selectedOs,
      String signupIntent,
      UUID trialTenantId) {
    Lead lead =
        Lead.create(
            companyName,
            taxId,
            organizationType,
            firstName,
            lastName,
            workEmail,
            selectedOs,
            signupIntent,
            trialTenantId);
    return toDto(leadRepository.save(lead));
  }

  private LeadDto toDto(Lead lead) {
    return new LeadDto(
        lead.getId(),
        lead.getUid(),
        lead.getCompanyName(),
        lead.getTaxId(),
        lead.getOrganizationType(),
        lead.getFirstName(),
        lead.getLastName(),
        lead.getWorkEmail(),
        lead.getSelectedOs(),
        lead.getSignupIntent(),
        lead.getTrialTenantId(),
        lead.getCreatedAt());
  }
}
