package com.fabricmanagement.production.masterdata.fiber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request for creating a NEW 100% fiber type (platform-level).
 *
 * <p><b>Create-Only:</b> Once created, cannot be updated or deleted by tenant users.</p>
 * <p>Only platform admins can modify these entries.</p>
 * <p>This creates a new fiber ISO code accessible by ALL tenants.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNewFiberTypeRequest {

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotBlank(message = "ISO code is required")
    @Size(max = 10, message = "ISO code must not exceed 10 characters")
    @Pattern(regexp = "^[A-Z0-9]{2,10}$", message = "ISO code must be uppercase alphanumeric (2-10 chars)")
    private String isoCode;

    @NotBlank(message = "Fiber name is required")
    @Size(max = 255, message = "Fiber name must not exceed 255 characters")
    private String fiberName;

    private String fiberType;

    private String description;

    @Builder.Default
    private Boolean isOfficialIso = false;

    private Integer displayOrder;
}

