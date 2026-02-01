package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.Assignable;
import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * UserContact junction entity - Links User to Contact.
 *
 * <p>Owned by User module. Contact entity lives in Communication (shared kernel).
 */
@Entity
@Table(
    name = "common_user_contact",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_user_contact_user", columnList = "user_id"),
      @Index(name = "idx_user_contact_contact", columnList = "contact_id"),
      @Index(name = "idx_user_contact_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserContactId.class)
public class UserContact extends BaseJunctionEntity implements Assignable {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "contact_id", nullable = false)
  private UUID contactId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_id", insertable = false, updatable = false)
  private Contact contact;

  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private Boolean isDefault = false;

  @Override
  public UUID getParentId() {
    return userId;
  }

  @Override
  public UUID getChildId() {
    return contactId;
  }

  @Override
  public Boolean getPrimaryFlag() {
    return isDefault;
  }

  @Override
  public void setPrimaryFlag(Boolean value) {
    this.isDefault = Boolean.TRUE.equals(value);
  }

  @Override
  protected String getModuleCode() {
    return "UCON";
  }
}
