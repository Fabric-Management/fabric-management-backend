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

/**
 * A company that receives goods for a customer, related to the customer with a role (CONSIGNEE-1
 * C1).
 *
 * <p>Both ends are tenant trading partners. The linked company keeps its own partner type: it may
 * be a {@link PartnerType#DELIVERY_PARTNER}, or a company that is also a customer or supplier of
 * the tenant. One company may be linked to many customers, in different roles.
 *
 * <p>The database holds the hard guards: a partial unique index over the active links of a
 * (customer, linked company, role) triple, a check that a partner is not linked to itself, and the
 * role check list.
 */
@Entity
@Table(
    name = "partner_link",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_partner_link_tenant", columnList = "tenant_id"),
      @Index(name = "idx_partner_link_customer", columnList = "customer_partner_id"),
      @Index(name = "idx_partner_link_linked", columnList = "linked_partner_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerLink extends BaseEntity {

  public static final int NOTE_MAX_LENGTH = 500;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_partner_id", nullable = false)
  private TradingPartner customerPartner;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "linked_partner_id", nullable = false)
  private TradingPartner linkedPartner;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 30)
  private PartnerLinkRole role;

  @Column(name = "note", length = NOTE_MAX_LENGTH)
  private String note;

  public static PartnerLink create(
      TradingPartner customerPartner,
      TradingPartner linkedPartner,
      PartnerLinkRole role,
      String note) {
    return PartnerLink.builder()
        .customerPartner(customerPartner)
        .linkedPartner(linkedPartner)
        .role(role)
        .note(note == null || note.isBlank() ? null : note.strip())
        .build();
  }

  @Override
  protected String getModuleCode() {
    return "PLNK";
  }
}
