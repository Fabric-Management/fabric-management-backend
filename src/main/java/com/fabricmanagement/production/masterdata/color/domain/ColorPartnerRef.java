package com.fabricmanagement.production.masterdata.color.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorPartnerRefDomainException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/** Partner-to-color relationship aggregate. Alias writes must always pass through this root. */
@Entity
@Table(name = "color_partner_ref", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ColorPartnerRef extends BaseEntity {

  @Column(name = "color_id", nullable = false, updatable = false)
  private UUID colorId;

  @Column(name = "partner_id", nullable = false, updatable = false)
  private UUID partnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, updatable = false, length = 20)
  private PartnerRole role;

  @Column(name = "delta_e_tolerance", precision = 4, scale = 2)
  private BigDecimal deltaETolerance;

  @OneToMany(
      mappedBy = "colorPartnerRef",
      cascade = CascadeType.ALL,
      orphanRemoval = false,
      fetch = FetchType.LAZY)
  @OrderBy("createdAt ASC")
  @BatchSize(size = 50)
  private List<ColorPartnerCode> codes = new ArrayList<>();

  @Transient private UUID preparedPrimaryTargetId;

  public static ColorPartnerRef create(
      UUID tenantId,
      UUID colorId,
      UUID partnerId,
      PartnerRole role,
      BigDecimal deltaETolerance,
      String initialCode,
      String initialName) {
    if (colorId == null || partnerId == null || role == null) {
      throw ColorPartnerRefDomainException.invalid("Color, partner, and role are required");
    }
    ColorPartnerRef ref = new ColorPartnerRef();
    ref.colorId = colorId;
    ref.partnerId = partnerId;
    ref.role = role;
    ref.setTenantId(tenantId);
    ref.onCreate();
    ref.updateToleranceInternal(deltaETolerance);
    ref.codes.add(ColorPartnerCode.create(ref, tenantId, initialCode, initialName, true));
    return ref;
  }

  public ColorPartnerCode addCode(String externalCode, String externalName) {
    requireActive();
    String key = ColorPartnerCode.keyOf(externalCode);
    if (codes.stream()
        .anyMatch(
            code ->
                Boolean.TRUE.equals(code.getIsActive()) && code.getExternalCodeKey().equals(key))) {
      throw ColorPartnerRefDomainException.duplicateCode(externalCode);
    }
    boolean firstActive = codes.stream().noneMatch(code -> Boolean.TRUE.equals(code.getIsActive()));
    ColorPartnerCode code =
        ColorPartnerCode.create(this, getTenantId(), externalCode, externalName, firstActive);
    codes.add(code);
    return code;
  }

  public void updateCodeName(UUID codeId, String externalName) {
    requireActive();
    activeCode(codeId).updateName(externalName);
  }

  public void updateTolerance(BigDecimal value) {
    requireActive();
    updateToleranceInternal(value);
  }

  public void preparePrimarySwitch(UUID targetCodeId) {
    requireActive();
    ColorPartnerCode target = activeCode(targetCodeId);
    if (target.isPrimary()) {
      preparedPrimaryTargetId = targetCodeId;
      return;
    }
    codes.stream().filter(ColorPartnerCode::isPrimary).forEach(ColorPartnerCode::demote);
    preparedPrimaryTargetId = targetCodeId;
  }

  public void completePrimarySwitch(UUID targetCodeId) {
    requireActive();
    if (preparedPrimaryTargetId == null || !preparedPrimaryTargetId.equals(targetCodeId)) {
      throw ColorPartnerRefDomainException.conflict(
          "Primary switch completion does not match a prepared target");
    }
    activeCode(targetCodeId).promote();
    preparedPrimaryTargetId = null;
  }

  public void deactivateCode(UUID codeId) {
    requireActive();
    ColorPartnerCode code = activeCode(codeId);
    if (code.isPrimary()) {
      throw ColorPartnerRefDomainException.conflict(
          "The active primary code cannot be deactivated; switch primary first");
    }
    code.deactivate();
  }

  public void deactivate() {
    requireActive();
    codes.stream()
        .filter(code -> Boolean.TRUE.equals(code.getIsActive()))
        .forEach(ColorPartnerCode::deactivate);
    delete();
  }

  public void reactivateWithExistingCode(UUID codeId) {
    requireInactive();
    ColorPartnerCode selected = code(codeId);
    if (Boolean.TRUE.equals(selected.getIsActive())) {
      throw ColorPartnerRefDomainException.conflict(
          "Only an inactive retained code can reactivate a relationship");
    }
    codes.forEach(
        code -> {
          if (Boolean.TRUE.equals(code.getIsActive())) {
            code.deactivate();
          } else {
            code.demote();
          }
        });
    selected.reactivateAsPrimary();
    activate();
  }

  public ColorPartnerCode reactivateWithNewCode(String externalCode, String externalName) {
    requireInactive();
    String key = ColorPartnerCode.keyOf(externalCode);
    if (codes.stream().anyMatch(code -> code.getExternalCodeKey().equals(key))) {
      throw ColorPartnerRefDomainException.conflict(
          "This aggregate already retains that code; reactivate it by ID instead");
    }
    codes.forEach(ColorPartnerCode::demote);
    ColorPartnerCode selected =
        ColorPartnerCode.create(this, getTenantId(), externalCode, externalName, true);
    codes.add(selected);
    activate();
    return selected;
  }

  public ColorPartnerCode primaryCode() {
    return codes.stream()
        .filter(code -> Boolean.TRUE.equals(code.getIsActive()) && code.isPrimary())
        .findFirst()
        .orElseThrow(
            () ->
                ColorPartnerRefDomainException.conflict("Active relationship has no primary code"));
  }

  private void updateToleranceInternal(BigDecimal value) {
    if (value != null && value.signum() <= 0) {
      throw ColorPartnerRefDomainException.invalid(
          "Partner Delta-E tolerance must be greater than zero");
    }
    if (value != null && value.compareTo(new BigDecimal("99.99")) > 0) {
      throw ColorPartnerRefDomainException.invalid(
          "Partner Delta-E tolerance must not exceed 99.99");
    }
    if (value != null && value.scale() > 2) {
      throw ColorPartnerRefDomainException.invalid(
          "Partner Delta-E tolerance must not have more than two decimal places");
    }
    this.deltaETolerance = value;
  }

  private ColorPartnerCode activeCode(UUID codeId) {
    ColorPartnerCode code = code(codeId);
    if (!Boolean.TRUE.equals(code.getIsActive())) {
      throw ColorPartnerRefDomainException.conflict("Partner code is inactive: " + codeId);
    }
    return code;
  }

  private ColorPartnerCode code(UUID codeId) {
    return codes.stream()
        .filter(candidate -> Objects.equals(candidate.getId(), codeId))
        .findFirst()
        .orElseThrow(() -> ColorPartnerRefDomainException.codeNotFound(String.valueOf(codeId)));
  }

  private void requireActive() {
    if (!Boolean.TRUE.equals(getIsActive())) {
      throw ColorPartnerRefDomainException.conflict(
          "Inactive partner references can only be reactivated");
    }
  }

  private void requireInactive() {
    if (Boolean.TRUE.equals(getIsActive())) {
      throw ColorPartnerRefDomainException.conflict("Partner reference is already active");
    }
  }

  @Override
  protected String getModuleCode() {
    return "CPR";
  }
}
