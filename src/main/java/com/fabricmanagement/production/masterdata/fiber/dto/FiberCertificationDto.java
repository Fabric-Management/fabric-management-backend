package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberCertificationDto {

  private UUID id;
  private String certificationCode;
  private String certificationName;
  private String certifyingBody;
  private String description;
  private Integer displayOrder;

  public static FiberCertificationDto from(FiberCertification entity) {
    return FiberCertificationDto.builder()
        .id(entity.getId())
        .certificationCode(entity.getCertificationCode())
        .certificationName(entity.getCertificationName())
        .certifyingBody(entity.getCertifyingBody())
        .description(entity.getDescription())
        .displayOrder(entity.getDisplayOrder())
        .build();
  }
}
