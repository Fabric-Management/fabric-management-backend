package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.Assignable;
import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * CompanyContact junction entity - Links Company to Contact.
 *
 * <p>Owned by Company module. Contact entity lives in Communication (shared kernel).
 */
@Entity
@Table(
    name = "common_company_contact",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_company_contact_company", columnList = "company_id"),
      @Index(name = "idx_company_contact_contact", columnList = "contact_id"),
      @Index(name = "idx_company_contact_tenant", columnList = "tenant_id"),
      @Index(name = "idx_company_contact_department", columnList = "department")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(CompanyContactId.class)
public class CompanyContact extends BaseJunctionEntity implements Assignable {

  @Id
  @Column(name = "company_id", nullable = false)
  private UUID companyId;

  @Id
  @Column(name = "contact_id", nullable = false)
  private UUID contactId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

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
    return companyId;
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
    return "CCON";
  }
}
