package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

@Schema(description = "Available persona for playground impersonation")
public record PlaygroundPersonaDto(
    @Schema(
            description = "User ID",
            requiredMode = RequiredMode.REQUIRED,
            example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,
    @Schema(
            description = "Display name of the persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "Jane Smith")
        String name,
    @Schema(
            description = "Email address of the persona",
            requiredMode = RequiredMode.REQUIRED,
            example = "jane.smith@nexusfabrics.com")
        String email,
    @Schema(description = "Role name", requiredMode = RequiredMode.REQUIRED, example = "Manager")
        String role,
    @Schema(
            description = "Job title (human-readable position name)",
            requiredMode = RequiredMode.REQUIRED,
            example = "Spinning Mill Manager")
        String jobTitle,
    @Schema(
            description = "Primary department name",
            requiredMode = RequiredMode.REQUIRED,
            example = "Yarn Department")
        String department,
    @Schema(
            description = "Department group: PRODUCTION or SUPPORT",
            requiredMode = RequiredMode.NOT_REQUIRED,
            example = "PRODUCTION")
        String departmentGroup,
    @Schema(
            description = "User type: INTERNAL or EXTERNAL",
            requiredMode = RequiredMode.REQUIRED,
            example = "INTERNAL")
        String userType,
    @Schema(
            description = "Organization name the user belongs to",
            requiredMode = RequiredMode.REQUIRED,
            example = "Nexus Fabrics")
        String organizationName) {}
