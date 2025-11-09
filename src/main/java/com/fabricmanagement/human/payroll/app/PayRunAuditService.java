package com.fabricmanagement.human.payroll.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.payroll.domain.PayRun;
import com.fabricmanagement.human.payroll.domain.PayRunAuditLog;
import com.fabricmanagement.human.payroll.infra.repository.PayRunAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PayRunAuditService {

    private final PayRunAuditLogRepository auditLogRepository;
    private final Clock clock;

    public void record(PayRun payRun, String action, String message, String payloadJson) {
        PayRunAuditLog logEntry = PayRunAuditLog.builder()
            .payRun(payRun)
            .action(action)
            .actorId(TenantContext.getCurrentUserId())
            .message(message)
            .payload(payloadJson)
            .occurredAt(Instant.now(clock))
            .build();
        logEntry.setTenantId(payRun.getTenantId());
        auditLogRepository.save(logEntry);
    }
}

