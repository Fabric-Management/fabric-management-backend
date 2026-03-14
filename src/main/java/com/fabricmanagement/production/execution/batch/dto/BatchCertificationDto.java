package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationChangeReason;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCertificationDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCertificationDto {

  private UUID id;
  private UUID batchId;
  private UUID certificationId;
  private FiberCertificationDto certification;
  private BatchCertificationScope scope;
  private UUID partnerCertificationId;
  private UUID orgCertificationId;
  private String certNumber;
  private LocalDate validFrom;
  private LocalDate validUntil;
  private String certifyingBodyRef;
  private String documentUrl;
  private String remarks;
  private BatchCertificationChangeReason changeReason;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static BatchCertificationDto from(BatchCertification entity) {
    return BatchCertificationDto.builder()
        .id(entity.getId())
        .batchId(entity.getBatch() != null ? entity.getBatch().getId() : null)
        .certificationId(
            entity.getCertification() != null ? entity.getCertification().getId() : null)
        .certification(
            entity.getCertification() != null
                ? FiberCertificationDto.from(entity.getCertification())
                : null)
        .scope(entity.getScope())
        .partnerCertificationId(
            entity.getPartnerCertification() != null
                ? entity.getPartnerCertification().getId()
                : null)
        .orgCertificationId(
            entity.getOrgCertification() != null ? entity.getOrgCertification().getId() : null)
        .certNumber(entity.getCertNumber())
        .validFrom(entity.getValidFrom())
        .validUntil(entity.getValidUntil())
        .certifyingBodyRef(entity.getCertifyingBodyRef())
        .documentUrl(entity.getDocumentUrl())
        .remarks(entity.getRemarks())
        .changeReason(entity.getChangeReason())
        .isActive(entity.getIsActive())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
