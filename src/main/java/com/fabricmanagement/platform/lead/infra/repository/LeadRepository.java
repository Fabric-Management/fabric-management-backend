package com.fabricmanagement.platform.lead.infra.repository;

import com.fabricmanagement.platform.lead.domain.Lead;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

  List<Lead> findByWorkEmail(String workEmail);

  List<Lead> findByTrialTenantId(UUID trialTenantId);
}
