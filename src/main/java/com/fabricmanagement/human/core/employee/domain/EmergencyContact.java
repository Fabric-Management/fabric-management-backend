package com.fabricmanagement.human.core.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyContact {

  @Column(name = "emergency_contact_name", length = 100)
  private String name;

  @Column(name = "emergency_contact_phone", length = 50)
  private String phone;

  @Column(name = "emergency_contact_relationship", length = 50)
  private String relationship;

  public boolean isEmpty() {
    return (name == null || name.isBlank())
        && (phone == null || phone.isBlank())
        && (relationship == null || relationship.isBlank());
  }
}
