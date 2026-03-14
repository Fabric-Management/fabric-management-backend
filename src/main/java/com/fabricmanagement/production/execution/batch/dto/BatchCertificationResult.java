package com.fabricmanagement.production.execution.batch.dto;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of add/update batch certification, carrying the DTO and optional warnings (e.g. expired
 * referenced supplier/facility certification for GOTS compliance).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCertificationResult {

  private BatchCertificationDto data;
  private List<String> warnings;

  public static BatchCertificationResult of(BatchCertificationDto data) {
    return BatchCertificationResult.builder().data(data).warnings(Collections.emptyList()).build();
  }

  public static BatchCertificationResult of(BatchCertificationDto data, List<String> warnings) {
    return BatchCertificationResult.builder()
        .data(data)
        .warnings(warnings != null ? warnings : Collections.emptyList())
        .build();
  }
}
