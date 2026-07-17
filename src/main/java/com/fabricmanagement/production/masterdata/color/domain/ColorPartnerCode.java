package com.fabricmanagement.production.masterdata.color.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorPartnerRefDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable partner-code identity owned and mutated exclusively by {@link ColorPartnerRef}. */
@Entity
@Table(name = "color_partner_code", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ColorPartnerCode extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "color_partner_ref_id", nullable = false)
  private ColorPartnerRef colorPartnerRef;

  @Column(name = "partner_id", nullable = false, updatable = false)
  private UUID partnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, updatable = false, length = 20)
  private PartnerRole role;

  @Column(name = "external_code", nullable = false, updatable = false, length = 50)
  private String externalCode;

  @Column(name = "external_code_key", nullable = false, updatable = false, length = 50)
  private String externalCodeKey;

  @Column(name = "external_name", length = 255)
  private String externalName;

  @Column(name = "is_primary", nullable = false)
  private boolean primary;

  static ColorPartnerCode create(
      ColorPartnerRef owner,
      UUID tenantId,
      String externalCode,
      String externalName,
      boolean primary) {
    String display = normalizeCode(externalCode);
    ColorPartnerCode code = new ColorPartnerCode();
    code.colorPartnerRef = owner;
    code.partnerId = owner.getPartnerId();
    code.role = owner.getRole();
    code.externalCode = display;
    code.externalCodeKey = keyFromDisplay(display);
    code.externalName = normalizeName(externalName);
    code.primary = primary;
    code.setTenantId(tenantId);
    code.onCreate();
    return code;
  }

  void updateName(String externalName) {
    this.externalName = normalizeName(externalName);
  }

  void promote() {
    if (!Boolean.TRUE.equals(getIsActive())) {
      throw ColorPartnerRefDomainException.conflict("An inactive partner code cannot be primary");
    }
    this.primary = true;
  }

  void demote() {
    this.primary = false;
  }

  void deactivate() {
    demote();
    delete();
  }

  void reactivateAsPrimary() {
    activate();
    this.primary = true;
  }

  public static String keyOf(String externalCode) {
    return keyFromDisplay(normalizeCode(externalCode));
  }

  private static String keyFromDisplay(String display) {
    String key = display.toUpperCase(Locale.ROOT);
    if (key.length() > 50) {
      throw ColorPartnerRefDomainException.invalid(
          "Canonical external code must not exceed 50 characters");
    }
    return key;
  }

  private static String normalizeCode(String externalCode) {
    if (externalCode == null || externalCode.isBlank()) {
      throw ColorPartnerRefDomainException.invalid("External code must not be blank");
    }
    String normalized = externalCode.trim();
    if (normalized.length() > 50) {
      throw ColorPartnerRefDomainException.invalid("External code must not exceed 50 characters");
    }
    return normalized;
  }

  private static String normalizeName(String externalName) {
    if (externalName == null) {
      return null;
    }
    String normalized = externalName.trim();
    if (normalized.isEmpty()) {
      throw ColorPartnerRefDomainException.invalid("External name must be null or non-blank");
    }
    if (normalized.length() > 255) {
      throw ColorPartnerRefDomainException.invalid("External name must not exceed 255 characters");
    }
    return normalized;
  }

  @Override
  protected String getModuleCode() {
    return "CPC";
  }
}
