package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.platform.tenant.domain.BrandAppearance;
import com.fabricmanagement.platform.tenant.domain.BrandColourMode;
import com.fabricmanagement.platform.tenant.domain.BrandSvgOutlinePort;
import com.fabricmanagement.platform.tenant.domain.TenantBrandAsset;
import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** A stateless preparation step: previews never create or replace persisted artwork. */
@Service
@RequiredArgsConstructor
public class BrandSvgPreparationService {
  private final BrandSvgSanitizerService sanitizer;
  private final BrandSvgOutlinePort outliner;

  public byte[] prepare(MultipartFile file, BrandColourMode mode) {
    if (file == null || file.isEmpty()) {
      throw TenantBrandingDomainException.invalid("Choose a non-empty SVG");
    }
    if (file.getSize() > TenantBrandAsset.MAX_BYTES) {
      throw new TenantBrandingDomainException(
          "Choose an SVG of at most 512 KB", "TENANT_BRANDING_TOO_LARGE", 413);
    }
    if (file.getOriginalFilename() == null
        || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".svg")
        || file.getContentType() == null
        || !"image/svg+xml".equalsIgnoreCase(file.getContentType().split(";", 2)[0].trim())) {
      throw TenantBrandingDomainException.invalid("Only image/svg+xml files are accepted");
    }
    try {
      String prepared = sanitizer.prepareText(file.getBytes());
      if (sanitizer.containsText(prepared)) {
        prepared =
            sanitizer.sanitizeOutlined(outliner.outline(prepared).getBytes(StandardCharsets.UTF_8));
      } else {
        prepared = sanitizer.sanitize(prepared.getBytes(StandardCharsets.UTF_8));
      }
      // Persist ORIGINAL vectors; theme recolouring is applied by the existing content reader.
      // Check it before offering a preview so unsupported masks never reach confirmation.
      if (mode == BrandColourMode.MONOCHROME) sanitizer.monochrome(prepared, BrandAppearance.LIGHT);
      return prepared.getBytes(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw TenantBrandingDomainException.invalid("The SVG could not be read");
    }
  }
}
