package com.fabricmanagement.human.leave.app;

import com.fabricmanagement.human.leave.domain.LeaveAccrualLog;
import com.fabricmanagement.human.leave.domain.LeaveType;
import com.fabricmanagement.human.leave.infra.repository.LeaveAccrualLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveAccrualLogService {

    private final LeaveAccrualLogRepository accrualLogRepository;

    public LeaveAccrualLog record(UUID tenantId,
                                  UUID employeeId,
                                  LeaveType leaveType,
                                  BigDecimal accrualAmount,
                                  BigDecimal balanceAfter,
                                  String policyPackCode,
                                  Integer policyPackVersion,
                                  Instant occurredAt,
                                  String contextJson) {
        LeaveAccrualLog logEntry = LeaveAccrualLog.builder()
            .employeeId(employeeId)
            .leaveType(leaveType)
            .accrualAmount(accrualAmount)
            .balanceAfter(balanceAfter)
            .policyPackCode(policyPackCode)
            .policyPackVersion(policyPackVersion)
            .occurredAt(occurredAt)
            .context(contextJson)
            .build();
        logEntry.setTenantId(tenantId);
        return accrualLogRepository.save(logEntry);
    }
}

