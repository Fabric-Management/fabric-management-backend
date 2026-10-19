package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.*;
import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import com.fabricmanagement.platform.tenant.dto.TenantBrandAssetDto;
import com.fabricmanagement.platform.tenant.dto.TenantBrandingDto;
import com.fabricmanagement.platform.tenant.infra.repository.TenantBrandAssetLockRepository;
import com.fabricmanagement.platform.tenant.infra.repository.TenantBrandAssetRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TenantBrandingService {
  private static final String ASSETS = "/api/v1/common/tenant/settings/branding/assets/";
  private final TenantBrandAssetRepository repository;
  private final TenantBrandAssetLockRepository locks;
  private final BrandSvgSanitizerService sanitizer;
  private final BrandSvgPreparationService preparation;

  /** Preparing a preview does not write a tenant asset. */
  public byte[] preview(MultipartFile file, BrandColourMode mode) {
    return preparation.prepare(file, mode);
  }

  @Transactional(readOnly = true)
  public TenantBrandingDto read(boolean canManage) {
    var assets = repository.findByTenantIdAndIsActiveTrue(TenantContext.requireTenantId());
    var logo = slot(assets, BrandAssetKind.LOGO);
    var icon = slot(assets, BrandAssetKind.ICON);
    return new TenantBrandingDto(
        logo,
        icon,
        slot(assets, BrandAssetKind.LOGO_DARK),
        slot(assets, BrandAssetKind.ICON_DARK),
        logo == null ? null : logo.url(),
        icon == null ? null : icon.url(),
        canManage);
  }

  @Transactional
  public TenantBrandAssetDto upload(BrandAssetKind kind, BrandColourMode mode, MultipartFile file) {
    if (file == null || file.isEmpty())
      throw TenantBrandingDomainException.invalid("Choose an SVG file");
    if (file.getSize() > TenantBrandAsset.MAX_BYTES) {
      throw new TenantBrandingDomainException(
          "SVG files are limited to 512 KB", "TENANT_BRANDING_TOO_LARGE", 413);
    }
    String name = file.getOriginalFilename();
    if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".svg")) {
      throw TenantBrandingDomainException.invalid(
          "Only .svg files are accepted; export Illustrator artwork as SVG");
    }
    String type = file.getContentType();
    if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("image/svg+xml")) {
      throw TenantBrandingDomainException.invalid("Only image/svg+xml is accepted");
    }
    String svg;
    try {
      svg = sanitizer.sanitize(file.getBytes());
    } catch (IOException e) {
      throw TenantBrandingDomainException.invalid("The SVG could not be read");
    }
    if (mode == BrandColourMode.MONOCHROME) sanitizer.monochrome(svg, BrandAppearance.LIGHT);
    UUID tenantId = TenantContext.requireTenantId();
    locks.lock(tenantId, kind);
    TenantBrandAsset asset =
        repository
            .findByTenantIdAndKind(tenantId, kind)
            .orElseGet(() -> TenantBrandAsset.create(kind, mode, name, svg));
    asset.replace(mode, name, svg);
    asset.activate();
    return dto(repository.saveAndFlush(asset));
  }

  @Transactional
  public void remove(BrandAssetKind kind) {
    UUID tenantId = TenantContext.requireTenantId();
    locks.lock(tenantId, kind);
    repository
        .findByTenantIdAndKindAndIsActiveTrue(tenantId, kind)
        .ifPresent(
            asset -> {
              asset.delete();
              repository.save(asset);
            });
  }

  @Transactional(readOnly = true)
  public byte[] content(UUID id, BrandAppearance appearance) {
    TenantBrandAsset asset =
        repository
            .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), id)
            .orElseThrow(TenantBrandingDomainException::notFound);
    String svg =
        asset.getColourMode() == BrandColourMode.MONOCHROME
            ? sanitizer.monochrome(asset.getSvgContent(), appearance)
            : asset.getSvgContent();
    return svg.getBytes(StandardCharsets.UTF_8);
  }

  private TenantBrandAssetDto slot(java.util.List<TenantBrandAsset> assets, BrandAssetKind kind) {
    return assets.stream()
        .filter(asset -> asset.getKind() == kind)
        .findFirst()
        .map(this::dto)
        .orElse(null);
  }

  private TenantBrandAssetDto dto(TenantBrandAsset asset) {
    return new TenantBrandAssetDto(
        asset.getId(),
        asset.getKind(),
        asset.getColourMode(),
        asset.getFileName(),
        asset.getVersion(),
        ASSETS + asset.getId());
  }
}
