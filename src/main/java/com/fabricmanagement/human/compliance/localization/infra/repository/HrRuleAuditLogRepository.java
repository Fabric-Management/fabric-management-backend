package com.fabricmanagement.human.compliance.localization.infra.repository;

import com.fabricmanagement.human.compliance.localization.domain.HrRuleAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrRuleAuditLogRepository extends JpaRepository<HrRuleAuditLog, UUID> {}
