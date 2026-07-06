package com.fabricmanagement.platform.tradingpartner.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "partner_contact",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_partner_contact_tenant", columnList = "tenant_id"),
      @Index(name = "idx_partner_contact_partner", columnList = "partner_id"),
      @Index(name = "idx_partner_contact_role", columnList = "role")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerContact extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "partner_id", nullable = false)
  private TradingPartner partner;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "email", length = 254)
  private String email;

  @Column(name = "phone", length = 30)
  private String phone;

  @Column(name = "whatsapp_enabled", nullable = false)
  @Builder.Default
  private boolean whatsappEnabled = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 30)
  private PartnerContactRole role;

  @Column(name = "is_primary", nullable = false)
  @Builder.Default
  private boolean primaryContact = false;

  public static PartnerContact create(
      TradingPartner partner,
      String name,
      String email,
      String phone,
      PartnerContactRole role,
      boolean whatsappEnabled,
      boolean primary) {
    return PartnerContact.builder()
        .partner(partner)
        .name(name.strip())
        .email(email == null || email.isBlank() ? null : email.strip())
        .phone(phone == null || phone.isBlank() ? null : phone.strip())
        .role(role)
        .whatsappEnabled(whatsappEnabled)
        .primaryContact(primary)
        .build();
  }

  public boolean isPrimary() {
    return primaryContact;
  }

  public void setPrimary(boolean primary) {
    this.primaryContact = primary;
  }

  public void demotePrimary() {
    this.primaryContact = false;
  }

  @Override
  protected String getModuleCode() {
    return "PC";
  }
}
