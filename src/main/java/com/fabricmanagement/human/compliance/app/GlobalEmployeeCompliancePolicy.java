package com.fabricmanagement.human.compliance.app;

import com.fabricmanagement.human.compliance.domain.EmployeeComplianceContext;
import com.fabricmanagement.human.compliance.domain.EmployeeCompliancePolicy;
import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationConstants;
import com.fabricmanagement.human.core.employee.domain.EmergencyContact;
import com.fabricmanagement.human.core.employee.domain.Employee;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalEmployeeCompliancePolicy implements EmployeeCompliancePolicy {

  private static final List<String> RECOMMENDED_FIELDS =
      List.of("employeeNumber", "hireDate", "department", "emergencyContact");

  @Override
  public boolean supports(String countryCode) {
    if (countryCode == null || countryCode.isBlank()) {
      return true;
    }
    String normalized = countryCode.toUpperCase(Locale.ROOT);
    return HrLocalizationConstants.GLOBAL_COUNTRY_CODE.equals(normalized)
        || "DEFAULT".equals(normalized);
  }

  @Override
  public List<String> evaluate(Employee employee, EmployeeComplianceContext context) {
    List<String> missing = new ArrayList<>();
    if (employee.getEmployeeNumber() == null || employee.getEmployeeNumber().isBlank()) {
      missing.add("employeeNumber");
    }
    if (employee.getHireDate() == null) {
      missing.add("hireDate");
    }
    boolean departmentMissing = context.departmentValue().map(String::isBlank).orElse(true);
    if (departmentMissing) {
      missing.add("department");
    }
    if (isEmergencyContactMissing(employee.getEmergencyContact())) {
      missing.add("emergencyContact");
    }
    return missing.stream().filter(RECOMMENDED_FIELDS::contains).distinct().toList();
  }

  private boolean isEmergencyContactMissing(EmergencyContact contact) {
    return contact == null || contact.isEmpty();
  }
}
