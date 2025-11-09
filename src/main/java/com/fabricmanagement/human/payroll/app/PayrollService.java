package com.fabricmanagement.human.payroll.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackResolver;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackService;
import com.fabricmanagement.human.compliance.localization.app.ResolvedPolicyPack;
import com.fabricmanagement.human.payroll.domain.PayPeriod;
import com.fabricmanagement.human.payroll.domain.PayRun;
import com.fabricmanagement.human.payroll.domain.PayRunPayout;
import com.fabricmanagement.human.payroll.infra.repository.PayRunPayoutRepository;
import com.fabricmanagement.human.payroll.strategy.PayrollContext;
import com.fabricmanagement.human.payroll.strategy.PayrollResult;
import com.fabricmanagement.human.payroll.strategy.PayrollStrategy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollService {

    private final PayRunService payRunService;
    private final PayRunPayoutRepository payRunPayoutRepository;
    private final PayrollStrategyRegistry strategyRegistry;
    private final PayRunAuditService auditService;
    private final PayrollComplianceService payrollComplianceService;
    private final HrPolicyPackService hrPolicyPackService;
    private final HrPolicyPackResolver hrPolicyPackResolver;
    private final ObjectMapper objectMapper;

    @Transactional
    public PayrollResult executePayRun(UUID payRunId,
                                       List<UUID> employeeIds,
                                       Map<String, Object> policyParameters) {
        PayRun payRun = payRunService.getPayRun(payRunId);
        PayPeriod period = payRun.getPayPeriod();
        payrollComplianceService.validatePayRun(payRun);
        UUID tenantId = TenantContext.getCurrentTenantId();
        var policyPack = hrPolicyPackService.findActiveByPackCode(tenantId, payRun.getPolicyPackCode())
            .orElseThrow(() -> new IllegalStateException("Active policy pack not found for code " + payRun.getPolicyPackCode()));
        ResolvedPolicyPack resolvedPack = hrPolicyPackResolver.resolve(tenantId, policyPack);

        Map<String, Object> mergedParameters = mergePolicyParameters(resolvedPack, policyParameters);

        PayrollContext context = new PayrollContext(
            tenantId,
            payRunId,
            period.getCountryCode(),
            period.getStartDate(),
            period.getEndDate(),
            payRun.getPolicyPackCode(),
            payRun.getPolicyPackVersion(),
            employeeIds,
            mergedParameters
        );

        PayrollStrategy strategy = strategyRegistry.resolve(period.getCountryCode());
        PayrollResult result = strategy.execute(context);

        result.netAmounts().forEach((employeeId, netAmount) -> {
            PayRunPayout payout = payRunPayoutRepository.findByPayRunAndEmployee(payRun, employeeId)
                .orElseGet(() -> {
                    PayRunPayout newPayout = PayRunPayout.builder()
                        .payRun(payRun)
                        .employeeId(employeeId)
                        .netAmount(BigDecimal.ZERO)
                        .currency(resolveCurrency(mergedParameters))
                        .status("PENDING")
                        .build();
                    newPayout.setTenantId(payRun.getTenantId());
                    return newPayout;
                });
            payout.setNetAmount(netAmount);
            payout.setCurrency(resolveCurrency(mergedParameters));
            payRunPayoutRepository.save(payout);
        });

        auditService.record(payRun, "EXECUTE",
            "Payroll executed via strategy " + strategy.getClass().getSimpleName(),
            serializeMetadata(result, resolvedPack));

        payRunService.updateStatus(payRunId, com.fabricmanagement.human.payroll.domain.PayRunStatus.VALIDATED, null);
        return result;
    }

    private String serializeMetadata(PayrollResult result, ResolvedPolicyPack resolvedPack) {
        Map<String, Object> metadata = new HashMap<>();
        if (result.metadata() != null) {
            metadata.put("strategyMetadata", result.metadata());
        }
        metadata.put("policyPackCode", resolvedPack.packCode());
        metadata.put("policyPackVersion", resolvedPack.packVersion());
        metadata.put("policyPackLineage", resolvedPack.lineageCodes());
        return metadata.toString();
    }

    private String resolveCurrency(Map<String, Object> parameters) {
        if (parameters != null && parameters.containsKey("currency")) {
            return parameters.get("currency").toString().toUpperCase(Locale.ROOT);
        }
        return "USD";
    }

    private Map<String, Object> mergePolicyParameters(ResolvedPolicyPack resolvedPack, Map<String, Object> policyParameters) {
        Map<String, Object> merged = new HashMap<>(parsePolicyPayload(resolvedPack.resolvedPayload()));
        if (policyParameters != null) {
            merged.putAll(policyParameters);
        }
        merged.put("policyPackLineage", resolvedPack.lineageCodes());
        merged.put("policyPackCode", resolvedPack.packCode());
        return merged;
    }

    private Map<String, Object> parsePolicyPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse payroll policy payload: {}", ex.getMessage());
            return new HashMap<>();
        }
    }
}

