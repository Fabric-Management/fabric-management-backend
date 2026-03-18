package com.fabricmanagement.iwm.location.dto;

import com.fabricmanagement.iwm.location.domain.LocationStatus;
import com.fabricmanagement.iwm.location.domain.StorageCondition;
import com.fabricmanagement.iwm.location.domain.WarehouseLocation;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseLocationTreeDto {

  private UUID id;
  private String uid;
  private UUID parentId;
  private String code;
  private String name;
  private String description;
  private WarehouseLocationType type;
  private LocationStatus status;
  private StorageCondition storageCondition;
  private String path;
  private Integer level;
  private Integer sortOrder;
  private String barcode;
  private BigDecimal maxWeightKg;
  private BigDecimal currentWeightKg;
  private BigDecimal maxVolumeM3;
  private BigDecimal currentVolumeM3;
  private double utilizationPercent;
  private UUID linkedMachineId;
  private boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;
  private int childCount;

  @Builder.Default private List<WarehouseLocationTreeDto> children = new ArrayList<>();

  public static WarehouseLocationTreeDto from(WarehouseLocation entity) {
    return WarehouseLocationTreeDto.builder()
        .id(entity.getId())
        .uid(entity.getUid())
        .parentId(entity.getParentId())
        .code(entity.getCode())
        .name(entity.getName())
        .description(entity.getDescription())
        .type(entity.getType())
        .status(entity.getStatus())
        .storageCondition(entity.getStorageCondition())
        .path(entity.getPath())
        .level(entity.getLevel())
        .sortOrder(entity.getSortOrder())
        .barcode(entity.getBarcode())
        .maxWeightKg(entity.getMaxWeightKg())
        .currentWeightKg(entity.getCurrentWeightKg())
        .maxVolumeM3(entity.getMaxVolumeM3())
        .currentVolumeM3(entity.getCurrentVolumeM3())
        .utilizationPercent(entity.getUtilizationPercent())
        .linkedMachineId(entity.getLinkedMachineId())
        .isActive(entity.getIsActive())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .children(new ArrayList<>())
        .build();
  }
}
