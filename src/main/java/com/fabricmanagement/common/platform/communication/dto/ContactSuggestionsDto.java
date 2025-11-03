package com.fabricmanagement.common.platform.communication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for contact suggestions response.
 *
 * <p>Contains smart suggestions for phone and email based on company contacts.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSuggestionsDto {
    private PhoneSuggestion phoneSuggestion;
    private List<String> emailSuggestions;
}

