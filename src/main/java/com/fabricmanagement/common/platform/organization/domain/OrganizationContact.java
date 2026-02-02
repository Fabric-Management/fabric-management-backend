package com.fabricmanagement.common.platform.organization.domain;

import com.fabricmanagement.common.infrastructure.persistence.Assignable;
import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * OrganizationContact junction entity - Links Organization to Contact.
 *
 * <p>Owned by Organization module. Contact entity lives in Communication (shared kernel).
 */
@Entity
@Table(
    name = "common_organization_contact",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_org_contact_org", columnList = "organization_id"),
      @Index(name = "idx_org_contact_contact", columnList = "contact_id"),
      @Index(name = "idx_org_contact_tenant", columnList = "tenant_id"),
      @Index(name = "idx_org_contact_department", columnList = "department")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(OrganizationContactId.class)
public class OrganizationContact extends BaseJunctionEntity implements Assignable {

  @Id
  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Id
  @Column(name = "contact_id", nullable = false)
  private UUID contactId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", insertable = false, updatable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_id", insertable = false, updatable = false)
  private Contact contact;

  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private Boolean isDefault = false;

  @Column(name = "department", length = 100)
  private String department;

  @Override
  public UUID getParentId() {
    return organizationId;
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
    return "OCON";
  }
}
