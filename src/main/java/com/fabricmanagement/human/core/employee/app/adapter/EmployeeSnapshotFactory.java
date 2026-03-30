package com.fabricmanagement.human.core.employee.app.adapter;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmployeeSnapshotFactory {

  public static EmployeeSnapshot fromEntity(Employee e) {
    if (e == null || e.getUserId() == null) {
      return EmployeeSnapshot.absent();
    }
    var ec = e.getEmergencyContact();
    EmergencyContactData ecData =
        ec != null && !ec.isEmpty()
            ? new EmergencyContactData(ec.getName(), ec.getPhone(), ec.getRelationship())
            : null;
    return new EmployeeSnapshot(
        e.getUserId(),
        e.getTitle(),
        e.getGender(),
        e.getBirthDate(),
        e.getNationality(),
        e.getEmployeeNumber(),
        e.getHireDate(),
        ecData);
  }
}
