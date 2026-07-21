package com.fabricmanagement.iwm.location.dto;

import com.fabricmanagement.iwm.location.domain.StorageCondition;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWarehouseLocationRequest {

  private UUID parentId;

  @NotBlank(message = "Code is required")
  @Size(max = 100, message = "Code must not exceed 100 characters")
  @Pattern(
      regexp = "^[A-Za-z0-9_-]+$",
      message = "Code may only contain letters, digits, hyphens, and underscores")
  private String code;

  @NotBlank(message = "Name is required")
  @Size(max = 255, message = "Name must not exceed 255 characters")
  private String name;

  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  @NotNull(message = "Type is required")
  private WarehouseLocationType type;

  private StorageCondition storageCondition;

  @Size(max = 100, message = "Barcode must not exceed 100 characters")
  private String barcode;

  private UUID addressId;

  @PositiveOrZero(message = "Max weight must be zero or positive")
  private BigDecimal maxWeightKg;

  @PositiveOrZero(message = "Max volume must be zero or positive")
  private BigDecimal maxVolumeM3;

  private Integer sortOrder;

  private UUID linkedMachineId;

  /** Marks this storage location as an approved QC/quarantine destination. */
  private boolean qualityArea;
}
