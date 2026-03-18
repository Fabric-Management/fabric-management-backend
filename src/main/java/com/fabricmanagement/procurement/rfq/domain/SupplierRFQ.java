package com.fabricmanagement.procurement.rfq.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "supplier_rfq", schema = "procurement")
@Getter
@Setter
@NoArgsConstructor
public class SupplierRFQ extends BaseEntity {

  @Column(name = "rfq_number", nullable = false, unique = true, length = 50)
  private String rfqNumber;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  /**
   * Fix #13 — moduleType artık String değil enum. DB'de STRING olarak tutulur, compile-time
   * güvenlik kazanılır.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", nullable = false, length = 50)
  private SupplierRFQModuleType moduleType;

  @Enumerated(EnumType.STRING)
  @Column(name = "rfq_type", nullable = false, length = 30)
  private SupplierRFQType rfqType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private SupplierRFQStatus status = SupplierRFQStatus.DRAFT;

  @Column(name = "deadline", nullable = false)
  private Instant deadline;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Type(JsonType.class)
  @Column(name = "attachments", columnDefinition = "jsonb", nullable = false)
  private String attachments = "[]";

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "rfq_id", nullable = false, updatable = false)
  private List<SupplierRFQLine> lines = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "rfq_id", nullable = false, updatable = false)
  private List<SupplierRFQRecipient> recipients = new ArrayList<>();

  public void addLine(SupplierRFQLine line) {
    this.lines.add(line);
  }

  public void addRecipient(SupplierRFQRecipient recipient) {
    this.recipients.add(recipient);
  }

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "PROCUREMENT";
  }
}
