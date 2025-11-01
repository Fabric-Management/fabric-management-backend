package com.fabricmanagement.common.platform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response containing masked contact information for a user.
 * 
 * <p>Used for password reset flow - allows user to select
 * which verified contact to use for password reset.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContactInfoResponse {

    /**
     * List of masked contact information.
     * Only verified contacts are included.
     */
    private List<MaskedContactInfo> contacts;
}

