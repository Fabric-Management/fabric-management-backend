package com.fabricmanagement.human.compliance.domain;

import com.fabricmanagement.human.core.employee.domain.Employee;
import java.util.List;

public interface EmployeeCompliancePolicy {

  boolean supports(String countryCode);

  List<String> evaluate(Employee employee, EmployeeComplianceContext context);
}
