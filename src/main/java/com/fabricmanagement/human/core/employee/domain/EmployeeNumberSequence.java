package com.fabricmanagement.human.core.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "human_employee_number_sequence",
    schema = "human",
    indexes = {@Index(name = "idx_emp_seq_tenant", columnList = "tenant_id", unique = true)})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeNumberSequence {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "next_sequence", nullable = false)
  @Builder.Default
  private Integer nextSequence = 1;

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  public Integer getNextAndIncrement() {
    Integer current = this.nextSequence;
    this.nextSequence = current + 1;
    this.updatedAt = Instant.now();
    return current;
  }

  public static EmployeeNumberSequence create(UUID tenantId) {
    return EmployeeNumberSequence.builder()
        .tenantId(tenantId)
        .nextSequence(1)
        .updatedAt(Instant.now())
        .build();
  }
}
