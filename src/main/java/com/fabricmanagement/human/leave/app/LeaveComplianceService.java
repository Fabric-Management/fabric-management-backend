package com.fabricmanagement.human.leave.app;

import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.leave.domain.LeaveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LeaveComplianceService {

  public void validateAgainstPolicyPack(LeaveType leaveType, HrPolicyPack policyPack) {
    if (policyPack == null) {
      throw new IllegalStateException("No active HR policy pack available for leave accrual.");
    }
    if (!leaveType.appliesToCountry(policyPack.getCountryCode())
        && !policyPack.getCountryCode().equalsIgnoreCase("GLOBAL")) {
      log.warn(
          "Leave type {} not configured for policy pack country {}",
          leaveType.getCode(),
          policyPack.getCountryCode());
    }
  }
}
