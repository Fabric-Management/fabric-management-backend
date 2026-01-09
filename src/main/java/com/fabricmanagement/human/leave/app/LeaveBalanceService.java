package com.fabricmanagement.human.leave.app;

import com.fabricmanagement.human.leave.domain.LeaveBalance;
import com.fabricmanagement.human.leave.domain.LeaveType;
import com.fabricmanagement.human.leave.infra.repository.LeaveBalanceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

  private final LeaveBalanceRepository leaveBalanceRepository;

  public LeaveBalance getOrCreateBalance(
      UUID tenantId, UUID employeeId, LeaveType leaveType, String countryCode) {
    return leaveBalanceRepository
        .findByEmployeeAndType(tenantId, employeeId, leaveType)
        .orElseGet(
            () -> {
              LeaveBalance balance = new LeaveBalance();
              balance.setTenantId(tenantId);
              balance.setEmployeeId(employeeId);
              balance.setLeaveType(leaveType);
              balance.setCountryCode(countryCode);
              return leaveBalanceRepository.save(balance);
            });
  }

  public void updateBalance(
      LeaveBalance balance,
      BigDecimal accrualAmount,
      BigDecimal carryOver,
      String policyPackCode,
      Integer policyPackVersion,
      Instant occurrence) {
    balance.applyAccrual(accrualAmount, policyPackCode, policyPackVersion, occurrence);
    if (carryOver != null) {
      balance.updateCarryOver(carryOver);
    }
    leaveBalanceRepository.save(balance);
  }

  public BigDecimal getCurrentBalance(LeaveBalance balance) {
    return balance.getBalanceDays();
  }
}
