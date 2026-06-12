package com.fabricmanagement.finance.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_number_counter", schema = "finance")
@IdClass(DocumentNumberCounterKey.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentNumberCounter {
  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Id
  @Column(name = "series", nullable = false, length = 10)
  private String series;

  @Id
  @Column(name = "year", nullable = false)
  private int year;

  @Column(name = "last_value", nullable = false)
  private long lastValue;
}
