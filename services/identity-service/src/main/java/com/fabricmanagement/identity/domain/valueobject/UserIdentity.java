package com.fabricmanagement.identity.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Value object representing user's core identity.
 * Encapsulates the primary identifying information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity {
    private UserId id;
    private UUID tenantId;
    private String username;
    private String firstName;
    private String lastName;
    
    /**
     * Creates a new user identity.
     */
    public static UserIdentity create(UUID tenantId, String username, String firstName, String lastName) {
        return UserIdentity.builder()
            .id(UserId.generate())
            .tenantId(tenantId)
            .username(username)
            .firstName(firstName)
            .lastName(lastName)
            .build();
    }
    
    /**
     * Gets the full name of the user.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}