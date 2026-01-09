package com.fabricmanagement.human.leave.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.leave.domain.LeaveBalance;
import com.fabricmanagement.human.leave.domain.LeaveType;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveOnboardingService {

  private final LeaveTypeService leaveTypeService;
  private final LeaveBalanceService leaveBalanceService;

  @Transactional
  public void initializeBalances(UUID employeeId, String countryOverride) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    String country =
        countryOverride != null
            ? countryOverride.toUpperCase(Locale.ROOT)
            : TenantContext.getCurrentTenantCountry();
    List<LeaveType> leaveTypes = leaveTypeService.findActiveForCountry(tenantId, country);
    leaveTypes.forEach(
        leaveType -> {
          LeaveBalance balance =
              leaveBalanceService.getOrCreateBalance(tenantId, employeeId, leaveType, country);
          log.debug(
              "Initialized leave balance: employeeId={}, leaveType={}, balanceId={}",
              employeeId,
              leaveType.getCode(),
              balance.getId());
        });
  }
}
