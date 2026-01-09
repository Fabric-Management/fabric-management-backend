package com.fabricmanagement.human.leave.attendance.domain;

public interface LeavePolicy {

  boolean supports(String countryCode, String leaveTypeCode);

  LeaveAccrualResult calculateAccrual(LeavePolicyRequest request);
}
