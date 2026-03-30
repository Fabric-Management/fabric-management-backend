package com.fabricmanagement.common.infrastructure.identity;

/**
 * Immutable shared data for emergency contact fields, exchanged across platform/user and
 * human/employee bounded contexts.
 */
public record EmergencyContactData(String name, String phone, String relationship) {

  public static EmergencyContactData empty() {
    return new EmergencyContactData(null, null, null);
  }

  public boolean isEmpty() {
    return (name == null || name.isBlank())
        && (phone == null || phone.isBlank())
        && (relationship == null || relationship.isBlank());
  }
}
