package com.fabricmanagement.sales.quote.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "quote_approval_token", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class QuoteApprovalToken extends BaseEntity {

  @Column(name = "quote_id", nullable = false)
  private UUID quoteId;

  @Column(name = "token", nullable = false, unique = true, length = 255)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 30)
  private QuoteApprovalChannel channel;

  @Column(name = "sent_to", length = 255)
  private String sentTo;

  @Column(name = "contact_id")
  private UUID contactId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private QuoteApprovalStatus status = QuoteApprovalStatus.PENDING;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "ip_address", length = 50)
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @Column(name = "customer_note", columnDefinition = "TEXT")
  private String customerNote;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private String location;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "SALES";
  }
}
