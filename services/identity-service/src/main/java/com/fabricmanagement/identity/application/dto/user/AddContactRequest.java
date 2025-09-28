package com.fabricmanagement.identity.application.dto.user;

import com.fabricmanagement.identity.domain.valueobject.ContactType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for adding user contact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddContactRequest {

    @NotBlank(message = "Contact type is required")
    private ContactType contactType;

    @NotBlank(message = "Contact value is required")
    private String contactValue;
}
