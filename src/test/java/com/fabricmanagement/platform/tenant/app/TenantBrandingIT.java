package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.*;
import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

class TenantBrandingIT extends AbstractIntegrationTest {
  @Autowired private TenantBrandingService service;
  @Autowired private TenantService settings;
  @Autowired private SystemTransactionExecutor system;
  private final UUID tenantA = UUID.randomUUID();
  private final UUID tenantB = UUID.randomUUID();
  private static final String SVG =
      "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'>"
          + "<path fill='#fff' d='M0 0 L10 0 L10 10 Z'/></svg>";

  @BeforeEach
  void tenants() {
    for (UUID id : java.util.List.of(tenantA, tenantB)) {
      system.executeInTransaction(
          jdbc ->
              jdbc.update(
                  "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) VALUES (?,"
                      + " ?, ?, 'Branding tenant', 'ACTIVE')",
                  id,
                  id.toString(),
                  id.toString()));
    }
  }

  @Test
  void uploadsAreTenantScoped_andGeneralSettingsCannotEraseThem() {
    var asset =
        TenantContext.executeInTenantContext(
            tenantA, () -> service.upload(BrandAssetKind.LOGO, BrandColourMode.MONOCHROME, file()));
    TenantContext.executeInTenantContext(
        tenantA,
        () -> {
          assertThat(service.read(true).logoUrl()).contains(asset.id().toString());
          settings.updateMySettings(TenantSettings.defaults());
          assertThat(service.read(true).logo().id()).isEqualTo(asset.id());
          assertThat(
                  new String(
                      service.content(asset.id(), BrandAppearance.LIGHT), StandardCharsets.UTF_8))
              .contains("#111111");
          return null;
        });
    TenantContext.executeInTenantContext(
        tenantB,
        () -> {
          assertThat(service.read(false).logo()).isNull();
          assertThatThrownBy(() -> service.content(asset.id(), BrandAppearance.LIGHT))
              .isInstanceOf(TenantBrandingDomainException.class)
              .hasMessage("Brand asset not found");
          return null;
        });
  }

  @Test
  void replacingAFileChangesItsVersion_andRemovalDoesNotRemoveTheOtherSlot() {
    TenantContext.executeInTenantContext(
        tenantA,
        () -> {
          var logo = service.upload(BrandAssetKind.LOGO, BrandColourMode.ORIGINAL, file());
          var replaced = service.upload(BrandAssetKind.LOGO, BrandColourMode.MONOCHROME, file());
          assertThat(replaced.id()).isEqualTo(logo.id());
          assertThat(replaced.version()).isGreaterThan(logo.version());
          service.upload(BrandAssetKind.ICON, BrandColourMode.ORIGINAL, file());
          service.remove(BrandAssetKind.LOGO);
          assertThat(service.read(false).logo()).isNull();
          assertThat(service.read(false).icon()).isNotNull();
          assertThatThrownBy(() -> service.content(logo.id(), BrandAppearance.ORIGINAL))
              .isInstanceOf(TenantBrandingDomainException.class);
          var reuploaded = service.upload(BrandAssetKind.LOGO, BrandColourMode.ORIGINAL, file());
          assertThat(reuploaded.id()).isEqualTo(logo.id());
          assertThat(reuploaded.version()).isGreaterThan(replaced.version());
          return null;
        });
  }

  @Test
  void declaredMimeTypeDoesNotMakeAnUnsafeFileValid() {
    TenantContext.executeInTenantContext(
        tenantA,
        () -> {
          var unsafe =
              new MockMultipartFile(
                  "file",
                  "logo.svg",
                  "image/svg+xml",
                  "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><script/></svg>"
                      .getBytes(StandardCharsets.UTF_8));
          assertThatThrownBy(
                  () -> service.upload(BrandAssetKind.LOGO, BrandColourMode.ORIGINAL, unsafe))
              .isInstanceOf(TenantBrandingDomainException.class);
          assertThat(service.read(false).logo()).isNull();
          return null;
        });
  }

  @Test
  void rowLevelSecurityHidesOtherTenants_evenWithoutARepositoryFilter() {
    var asset =
        TenantContext.executeInTenantContext(
            tenantA, () -> service.upload(BrandAssetKind.LOGO, BrandColourMode.ORIGINAL, file()));
    system.executeInTransaction(
        jdbc -> {
          jdbc.execute(
              "DO $$ BEGIN CREATE ROLE tenant_branding_rls_probe NOLOGIN NOBYPASSRLS; EXCEPTION"
                  + " WHEN duplicate_object THEN NULL; END $$");
          jdbc.execute("GRANT USAGE ON SCHEMA common_tenant TO tenant_branding_rls_probe");
          jdbc.execute(
              "GRANT SELECT, INSERT, UPDATE ON common_tenant.tenant_brand_asset TO"
                  + " tenant_branding_rls_probe");
          jdbc.execute("SET LOCAL ROLE tenant_branding_rls_probe");
          jdbc.queryForObject(
              "SELECT set_config('app.current_tenant', ?, true)", String.class, tenantB.toString());
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM common_tenant.tenant_brand_asset WHERE id = ?",
                      Long.class,
                      asset.id()))
              .isZero();
          assertThat(
                  jdbc.update(
                      "UPDATE common_tenant.tenant_brand_asset SET file_name = 'changed.svg' WHERE"
                          + " id = ?",
                      asset.id()))
              .isZero();
          jdbc.queryForObject(
              "SELECT set_config('app.current_tenant', ?, true)", String.class, tenantA.toString());
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM common_tenant.tenant_brand_asset WHERE id = ?",
                      Long.class,
                      asset.id()))
              .isEqualTo(1);
          return null;
        });
    assertThatThrownBy(
            () ->
                system.executeInTransaction(
                    jdbc -> {
                      jdbc.execute("SET LOCAL ROLE tenant_branding_rls_probe");
                      jdbc.queryForObject(
                          "SELECT set_config('app.current_tenant', ?, true)",
                          String.class,
                          tenantB.toString());
                      return jdbc.update(
                          "INSERT INTO common_tenant.tenant_brand_asset (id, tenant_id, created_at,"
                              + " updated_at, kind, colour_mode, file_name, svg_content) VALUES (?,"
                              + " ?, now(), now(), 'ICON', 'ORIGINAL', 'icon.svg', 'svg')",
                          UUID.randomUUID(),
                          tenantA);
                    }))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  private MockMultipartFile file() {
    return new MockMultipartFile(
        "file", "logo.svg", "image/svg+xml", SVG.getBytes(StandardCharsets.UTF_8));
  }
}
