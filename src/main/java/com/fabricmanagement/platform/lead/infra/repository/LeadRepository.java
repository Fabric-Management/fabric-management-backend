package com.fabricmanagement.platform.lead.infra.repository;

import com.fabricmanagement.platform.lead.domain.Lead;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

  Optional<Lead> findBySignupId(UUID signupId);

  Optional<Lead> findBySignupTenantIdAndSignupId(UUID tenantId, UUID signupId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Lead> findLockedBySignupTenantIdAndSignupId(UUID tenantId, UUID signupId);

  List<Lead> findByWorkEmail(String workEmail);

  List<Lead> findByTrialTenantId(UUID trialTenantId);
}
