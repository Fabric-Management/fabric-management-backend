package com.fabricmanagement.production.masterdata.color.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateColorRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String name,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colorHex) {}
