package com.fabricmanagement.human.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Emergency contact information.
 * 
 * <p>Stored as embedded value object in Employee entity.</p>
 */
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
    private String relationship; // e.g., "Spouse", "Parent", "Sibling", "Friend"
}

