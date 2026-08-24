package com.fabricmanagement.product.color.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.color.app.port.TradingPartnerQueryPort;
import com.fabricmanagement.product.color.domain.Color;
import com.fabricmanagement.product.color.domain.ColorPartnerCode;
import com.fabricmanagement.product.color.domain.ColorPartnerRef;
import com.fabricmanagement.product.color.domain.PartnerRole;
import com.fabricmanagement.product.color.domain.exception.ColorPartnerRefDomainException;
import com.fabricmanagement.product.color.dto.ColorPartnerForwardResolutionDto;
import com.fabricmanagement.product.color.dto.ColorPartnerReverseResolutionDto;
import com.fabricmanagement.product.color.infra.repository.ColorPartnerRefRepository;
import com.fabricmanagement.product.color.infra.repository.ColorRepository;
import com.fabricmanagement.product.color.mapper.ColorMapper;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ColorPartnerRefQueryService {

  private final ColorPartnerRefRepository colorPartnerRefRepository;
  private final ColorRepository colorRepository;
  private final TradingPartnerQueryPort tradingPartnerQueryPort;
  private final ColorMapper colorMapper;

  public Page<ColorPartnerRef> list(UUID colorId, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    colorRepository
        .findByTenantIdAndId(tenantId, colorId)
        .orElseThrow(() -> new NotFoundException("Color not found: " + colorId));
    Page<ColorPartnerRef> page =
        colorPartnerRefRepository.findByTenantIdAndColorId(tenantId, colorId, pageable);
    if (page.isEmpty()) {
      return page;
    }

    Map<UUID, ColorPartnerRef> refsWithCodes =
        colorPartnerRefRepository
            .findWithCodesByTenantIdAndIdIn(
                tenantId, page.getContent().stream().map(ColorPartnerRef::getId).toList())
            .stream()
            .collect(Collectors.toMap(ColorPartnerRef::getId, Function.identity()));

    return new PageImpl<>(
        page.getContent().stream().map(ref -> refsWithCodes.get(ref.getId())).toList(),
        pageable,
        page.getTotalElements());
  }

  public ColorPartnerReverseResolutionDto resolve(
      UUID partnerId, PartnerRole role, String externalCode) {
    UUID tenantId = TenantContext.requireTenantId();
    requirePartner(tenantId, partnerId, role);
    ColorPartnerCode matched =
        colorPartnerRefRepository
            .findActiveCodeForReverseLookup(
                tenantId, partnerId, role, ColorPartnerCode.keyOf(externalCode))
            .orElseThrow(
                () -> new NotFoundException("No active color mapping found for partner code"));
    ColorPartnerRef ref = matched.getColorPartnerRef();
    Color color =
        colorRepository
            .findByTenantIdAndIdAndIsActiveTrue(tenantId, ref.getColorId())
            .orElseThrow(() -> new NotFoundException("Resolved color is inactive or missing"));

    return new ColorPartnerReverseResolutionDto(
        colorMapper.toDto(color),
        matched.getExternalCode(),
        matched.getExternalName(),
        matched.isPrimary(),
        ref.primaryCode().getExternalCode(),
        ref.getId());
  }

  public ColorPartnerForwardResolutionDto forward(UUID colorId, UUID partnerId, PartnerRole role) {
    UUID tenantId = TenantContext.requireTenantId();
    requirePartner(tenantId, partnerId, role);
    ColorPartnerCode primary =
        colorPartnerRefRepository
            .findActivePrimaryForForwardLookup(tenantId, colorId, partnerId, role)
            .orElseThrow(() -> new NotFoundException("No active primary partner color code found"));
    ColorPartnerRef ref = primary.getColorPartnerRef();
    return new ColorPartnerForwardResolutionDto(
        ref.getId(),
        ref.getColorId(),
        ref.getPartnerId(),
        ref.getRole(),
        primary.getExternalCode(),
        primary.getExternalName());
  }

  private void requirePartner(UUID tenantId, UUID partnerId, PartnerRole role) {
    if (!tradingPartnerQueryPort.isActiveAndCompatible(tenantId, partnerId, role)) {
      throw ColorPartnerRefDomainException.unavailablePartner();
    }
  }
}
