package com.fabricmanagement.notification.hub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateLocalePreferenceRequest(
    @NotBlank
        @Pattern(regexp = "^(TR|EN|DE|FR|AR)$", message = "Desteklenen diller: TR, EN, DE, FR, AR")
        String locale,
    String dateFormat,
    String timezone) {}
