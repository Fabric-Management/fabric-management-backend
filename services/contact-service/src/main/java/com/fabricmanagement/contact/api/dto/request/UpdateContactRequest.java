package com.fabricmanagement.contact.api.dto.request;

import com.fabricmanagement.shared.infrastructure.constants.ValidationConstants;
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
public class UpdateContactRequest {
    
    @Size(max = ValidationConstants.MAX_EMAIL_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    @Pattern(regexp = ValidationConstants.EMAIL_PATTERN, message = ValidationConstants.MSG_INVALID_EMAIL)
    private String contactValue;
    
    private String contactType;
    
    private Boolean isPrimary;
    
    private Boolean isVerified;
}

