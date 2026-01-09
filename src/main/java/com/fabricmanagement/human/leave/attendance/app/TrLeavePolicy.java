package com.fabricmanagement.human.leave.attendance.app;

import com.fabricmanagement.human.leave.attendance.domain.LeaveAccrualResult;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicy;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicyRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 90)
public class TrLeavePolicy implements LeavePolicy {

  private final Clock clock;

  public TrLeavePolicy(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean supports(String countryCode, String leaveTypeCode) {
    return "TR".equalsIgnoreCase(countryCode);
  }

  @Override
  public LeaveAccrualResult calculateAccrual(LeavePolicyRequest request) {
    Map<String, Object> attributes = request.policyAttributes();
    double seniorityYears = calculateSeniorityYears(request);

    double baseLeaveDays = getLeaveDaysForSeniority(attributes, seniorityYears);
    double accrualPerMonth = baseLeaveDays / 12d;
    int probationDays = (int) getDouble(attributes, "leave.annual.probationDays", 60d);

    LocalDate hireDate = request.employmentStartDate();
    if (hireDate != null) {
      long daysEmployed =
          ChronoUnit.DAYS.between(
              hireDate, request.asOfDate().atZone(clock.getZone()).toLocalDate());
      if (daysEmployed < probationDays) {
        accrualPerMonth = 0;
      }
    }

    double newBalance = request.currentBalance() + accrualPerMonth;

    return new LeaveAccrualResult(
        request.leaveTypeCode(),
        accrualPerMonth,
        newBalance,
        request.asOfDate(),
        request.policyPackCode(),
        request.policyPackVersion(),
        attributes.toString());
  }

  private double calculateSeniorityYears(LeavePolicyRequest request) {
    LocalDate start = request.employmentStartDate();
    if (start == null) {
      return 0;
    }
    LocalDate today = request.asOfDate().atZone(clock.getZone()).toLocalDate();
    return ChronoUnit.DAYS.between(start, today) / 365.25;
  }

  private double getLeaveDaysForSeniority(Map<String, Object> attributes, double seniorityYears) {
    if (seniorityYears < 1) {
      return getDouble(attributes, "leave.annual.upto1", 14d);
    } else if (seniorityYears < 5) {
      return getDouble(attributes, "leave.annual.upto5", 14d);
    } else if (seniorityYears < 15) {
      return getDouble(attributes, "leave.annual.upto15", 20d);
    } else {
      return getDouble(attributes, "leave.annual.above15", 26d);
    }
  }

  protected double getDouble(Map<String, Object> attributes, String path, double defaultValue) {
    Object current = attributes;
    for (String segment : path.split("\\.")) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(segment);
      } else {
        return defaultValue;
      }
    }
    if (current instanceof Number number) {
      return number.doubleValue();
    }
    return defaultValue;
  }
}
