package com.fabricmanagement.human.core.employee.dto;

import com.fabricmanagement.human.core.employee.domain.Gender;
import com.fabricmanagement.human.core.employee.domain.HrComplianceStatus;
import com.fabricmanagement.human.core.employee.domain.Title;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeProfileDto {
  private UUID id;
  private UUID userId;
  private String employeeNumber;
  private Title title;
  private Gender gender;
  private LocalDate birthDate;
  private String nationality;
  private LocalDate hireDate;
  private LocalDate terminationDate;
  private HrComplianceStatus hrComplianceStatus;
  private String missingFields;

  public static EmployeeProfileDto from(
      com.fabricmanagement.human.core.employee.domain.Employee employee) {
    if (employee == null) {
      return null;
    }
    return EmployeeProfileDto.builder()
        .id(employee.getId())
        .userId(employee.getUserId())
        .employeeNumber(employee.getEmployeeNumber())
        .title(employee.getTitle())
        .gender(employee.getGender())
        .birthDate(employee.getBirthDate())
        .nationality(employee.getNationality())
        .hireDate(employee.getHireDate())
        .terminationDate(employee.getTerminationDate())
        .hrComplianceStatus(employee.getHrComplianceStatus())
        .missingFields(employee.getMissingFields())
        .build();
  }
}
