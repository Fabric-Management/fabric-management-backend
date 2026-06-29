package com.fabricmanagement.platform.lead.dto;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LeadDto(
    UUID id,
    String uid,
    String companyName,
    String taxId,
    OrganizationType organizationType,
    String firstName,
    String lastName,
    String workEmail,
    List<String> selectedOs,
    String signupIntent,
    UUID trialTenantId,
    Instant createdAt) {}
