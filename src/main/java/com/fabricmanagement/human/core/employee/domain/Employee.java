package com.fabricmanagement.human.core.employee.domain;

import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.platform.user.domain.JobTitlePreset;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "human_employee",
    schema = "human",
    indexes = {
      @Index(name = "idx_employee_user", columnList = "user_id", unique = true),
      @Index(name = "idx_employee_tenant", columnList = "tenant_id"),
      @Index(
          name = "idx_employee_employee_number",
          columnList = "tenant_id,employee_number",
          unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee extends BaseEntity {

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "title", length = 20)
  private Title title;

  @Enumerated(EnumType.STRING)
  @Column(name = "gender", length = 20)
  private Gender gender;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "nationality", length = 2)
  private String nationality;

  @Column(name = "employee_number", length = 50)
  private String employeeNumber;

  @Column(name = "hire_date")
  private LocalDate hireDate;

  @Column(name = "termination_date")
  private LocalDate terminationDate;

  @Embedded private EmergencyContact emergencyContact;

  @Enumerated(EnumType.STRING)
  @Column(name = "hr_compliance_status", length = 30)
  private HrComplianceStatus hrComplianceStatus;

  @Column(name = "missing_fields", length = 500)
  private String missingFields;

  @Column(name = "last_compliance_check_at")
  private Instant lastComplianceCheckAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_title_preset_id")
  private JobTitlePreset jobTitlePreset;

  public Integer calculateAge() {
    if (birthDate == null) {
      return null;
    }
    return Period.between(birthDate, LocalDate.now()).getYears();
  }

  public boolean isCurrentlyEmployed() {
    return terminationDate == null;
  }

  public void terminate(LocalDate terminationDate) {
    this.terminationDate = terminationDate;
  }

  public String getFormattedDisplayName(String firstName, String lastName) {
    if (title == null || title == Title.NONE) {
      return firstName + " " + lastName;
    }

    String titlePrefix =
        switch (title) {
          case MR -> "Mr.";
          case MISS -> "Miss";
          case MRS -> "Mrs.";
          case MS -> "Ms.";
          case DR -> "Dr.";
          case PROF -> "Prof.";
          case ENG -> "Eng.";
          case NONE -> "";
        };

    return titlePrefix + " " + firstName + " " + lastName;
  }

  public List<String> checkComplianceAndUpdateStatus(String department) {
    List<String> missingFieldsList = new java.util.ArrayList<>();

    if (employeeNumber == null || employeeNumber.isBlank()) {
      missingFieldsList.add("employeeNumber");
    }
    if (hireDate == null) {
      missingFieldsList.add("hireDate");
    }
    if (department == null || department.isBlank()) {
      missingFieldsList.add("department");
    }
    if (emergencyContact == null) {
      missingFieldsList.add("emergencyContact");
    }

    applyComplianceResult(missingFieldsList);
    return missingFieldsList;
  }

  public boolean isComplianceComplete() {
    return hrComplianceStatus == HrComplianceStatus.COMPLETE;
  }

  @Override
  protected String getModuleCode() {
    return "EMP";
  }

  public void applyComplianceResult(List<String> missingFieldsList) {
    this.missingFields =
        (missingFieldsList == null || missingFieldsList.isEmpty())
            ? null
            : String.join(",", missingFieldsList);
    this.lastComplianceCheckAt = Instant.now();
    if (missingFieldsList == null || missingFieldsList.isEmpty()) {
      this.hrComplianceStatus = HrComplianceStatus.COMPLETE;
    } else {
      this.hrComplianceStatus = HrComplianceStatus.MISSING_RECOMMENDED;
    }
  }
}
