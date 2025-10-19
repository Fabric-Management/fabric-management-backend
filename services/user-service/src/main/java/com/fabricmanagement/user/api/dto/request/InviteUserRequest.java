package com.fabricmanagement.user.api.dto.request;

import com.fabricmanagement.shared.infrastructure.constants.ValidationConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteUserRequest {

    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(max = ValidationConstants.MAX_NAME_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String firstName;

    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(max = ValidationConstants.MAX_NAME_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String lastName;

    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Email(message = ValidationConstants.MSG_INVALID_EMAIL)
    @Pattern(regexp = ValidationConstants.EMAIL_PATTERN, message = ValidationConstants.MSG_INVALID_EMAIL)
    @Size(max = ValidationConstants.MAX_EMAIL_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String email;

    @Pattern(regexp = ValidationConstants.PHONE_PATTERN, message = ValidationConstants.MSG_INVALID_PHONE)
    @Size(max = ValidationConstants.MAX_PHONE_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String phone;

    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    private String role;

    @Builder.Default
    private boolean sendVerification = true;

    private String preferredChannel; // EMAIL, SMS, WHATSAPP
}

