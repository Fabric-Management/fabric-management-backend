package com.fabricmanagement.finance.common.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentNumberCounterKey implements Serializable {
  private UUID tenantId;
  private String series;
  private int year;
}
