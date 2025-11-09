package com.fabricmanagement.human.compliance.localization.infra.repository;

import com.fabricmanagement.human.compliance.localization.domain.HrRuleAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HrRuleAuditLogRepository extends JpaRepository<HrRuleAuditLog, UUID> {
}

