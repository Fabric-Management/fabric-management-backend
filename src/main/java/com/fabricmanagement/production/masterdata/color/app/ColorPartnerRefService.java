package com.fabricmanagement.production.masterdata.color.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.color.app.port.TradingPartnerQueryPort;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerCode;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerRef;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorPartnerRefDomainException;
import com.fabricmanagement.production.masterdata.color.dto.AddColorPartnerCodeRequest;
import com.fabricmanagement.production.masterdata.color.dto.CreateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.dto.ReactivateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorPartnerCodeRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorPartnerRefRepository;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ColorPartnerRefService {

  private final ColorPartnerRefRepository colorPartnerRefRepository;
  private final ColorRepository colorRepository;
  private final TradingPartnerQueryPort tradingPartnerQueryPort;
  private final EntityManager entityManager;

  @Transactional
  public ColorPartnerRef create(UUID colorId, CreateColorPartnerRefRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    requireActiveColor(tenantId, colorId);
    requirePartner(tenantId, request.partnerId(), request.role());
    if (colorPartnerRefRepository.existsByTenantIdAndColorIdAndPartnerIdAndRole(
        tenantId, colorId, request.partnerId(), request.role())) {
      throw ColorPartnerRefDomainException.conflict(
          "A relationship already exists for this color, partner, and role");
    }
    requireCodeAvailable(
        tenantId, request.partnerId(), request.role(), request.initialPrimaryCode().externalCode());

    ColorPartnerRef ref =
        ColorPartnerRef.create(
            tenantId,
            colorId,
            request.partnerId(),
            request.role(),
            request.deltaETolerance(),
            request.initialPrimaryCode().externalCode(),
            request.initialPrimaryCode().externalName());
    return colorPartnerRefRepository.save(ref);
  }

  @Transactional
  public ColorPartnerRef update(UUID colorId, UUID refId, UpdateColorPartnerRefRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    ColorPartnerRef ref = loadForMutation(tenantId, colorId, refId);
    requireActiveColor(tenantId, colorId);
    requirePartner(tenantId, ref.getPartnerId(), ref.getRole());
    ref.updateTolerance(request.deltaETolerance());
    return colorPartnerRefRepository.save(ref);
  }

  @Transactional
  public ColorPartnerCode addCode(UUID colorId, UUID refId, AddColorPartnerCodeRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    ColorPartnerRef ref = loadForMutation(tenantId, colorId, refId);
    requireActiveColor(tenantId, colorId);
    requirePartner(tenantId, ref.getPartnerId(), ref.getRole());
    requireCodeAvailable(tenantId, ref.getPartnerId(), ref.getRole(), request.externalCode());
    ColorPartnerCode code = ref.addCode(request.externalCode(), request.externalName());
    entityManager.flush();
    return code;
  }

  @Transactional
  public ColorPartnerCode updateCode(
      UUID colorId, UUID refId, UUID codeId, UpdateColorPartnerCodeRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    ColorPartnerRef ref = loadForMutation(tenantId, colorId, refId);
    requireActiveColor(tenantId, colorId);
    requirePartner(tenantId, ref.getPartnerId(), ref.getRole());
    ref.updateCodeName(codeId, request.externalName());
    colorPartnerRefRepository.save(ref);
    return code(ref, codeId);
  }

  @Transactional
  public ColorPartnerRef makePrimary(UUID colorId, UUID refId, UUID codeId) {
    UUID tenantId = TenantContext.requireTenantId();
    ColorPartnerRef ref = loadForMutation(tenantId, colorId, refId);
    requireActiveColor(tenantId, colorId);
    requirePartner(tenantId, ref.getPartnerId(), ref.getRole());
    ref.preparePrimarySwitch(codeId);
    entityManager.flush();
    ref.completePrimarySwitch(codeId);
    return colorPartnerRefRepository.save(ref);
  }

  @Transactional
  public ColorPartnerRef deactivateCode(UUID colorId, UUID refId, UUID codeId) {
    UUID tenantId = TenantContext.requireTenantId();
    ColorPartnerRef ref = loadForMutation(tenantId, colorId, refId);
    ref.deactivateCode(codeId);
    return colorPartnerRefRepository.save(ref);
  }

  @Transactional
  public ColorPartnerRef deactivate(UUID colorId, UUID refId) {
    UUID tenantId = TenantContext.requireTenantId();
    ColorPartnerRef ref = loadForMutation(tenantId, colorId, refId);
    ref.deactivate();
    return colorPartnerRefRepository.save(ref);
  }

  @Transactional
  public ColorPartnerRef reactivate(
      UUID colorId, UUID refId, ReactivateColorPartnerRefRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    ColorPartnerRef ref = loadForMutation(tenantId, colorId, refId);
    requireActiveColor(tenantId, colorId);
    requirePartner(tenantId, ref.getPartnerId(), ref.getRole());

    if ((request.existingCodeId() == null) == (request.newPrimaryCode() == null)) {
      throw ColorPartnerRefDomainException.invalid(
          "Exactly one reactivation code alternative is required");
    }

    if (request.existingCodeId() != null) {
      ColorPartnerCode selected = code(ref, request.existingCodeId());
      requireCodeAvailable(tenantId, ref.getPartnerId(), ref.getRole(), selected.getExternalCode());
      ref.reactivateWithExistingCode(request.existingCodeId());
    } else if (request.newPrimaryCode() != null) {
      requireCodeAvailable(
          tenantId, ref.getPartnerId(), ref.getRole(), request.newPrimaryCode().externalCode());
      ref.reactivateWithNewCode(
          request.newPrimaryCode().externalCode(), request.newPrimaryCode().externalName());
    }
    return colorPartnerRefRepository.save(ref);
  }

  private ColorPartnerRef loadForMutation(UUID tenantId, UUID colorId, UUID refId) {
    ColorPartnerRef ref =
        colorPartnerRefRepository
            .findForMutationByTenantIdAndId(tenantId, refId)
            .orElseThrow(
                () -> new NotFoundException("Color partner reference not found: " + refId));
    if (!ref.getColorId().equals(colorId)) {
      throw new NotFoundException("Color partner reference not found for color: " + colorId);
    }
    return ref;
  }

  private void requireActiveColor(UUID tenantId, UUID colorId) {
    colorRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, colorId)
        .orElseThrow(() -> new NotFoundException("Active color not found: " + colorId));
  }

  private void requirePartner(UUID tenantId, UUID partnerId, PartnerRole role) {
    if (!tradingPartnerQueryPort.isActiveAndCompatible(tenantId, partnerId, role)) {
      throw ColorPartnerRefDomainException.unavailablePartner();
    }
  }

  private void requireCodeAvailable(
      UUID tenantId, UUID partnerId, PartnerRole role, String externalCode) {
    String key = ColorPartnerCode.keyOf(externalCode);
    if (colorPartnerRefRepository.existsActiveCode(tenantId, partnerId, role, key)) {
      throw ColorPartnerRefDomainException.duplicateCode(externalCode);
    }
  }

  private ColorPartnerCode code(ColorPartnerRef ref, UUID codeId) {
    return ref.getCodes().stream()
        .filter(candidate -> Objects.equals(candidate.getId(), codeId))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Color partner code not found: " + codeId));
  }
}
