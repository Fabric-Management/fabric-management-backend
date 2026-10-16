package com.fabricmanagement.platform.tenant.api.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.platform.tenant.app.TenantBrandingService;
import com.fabricmanagement.platform.tenant.domain.BrandAssetKind;
import com.fabricmanagement.platform.tenant.domain.BrandColourMode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class TenantBrandingControllerTest {
  private final TenantBrandingService service = mock(TenantBrandingService.class);
  private final SpELPermissionEvaluator permissions = mock(SpELPermissionEvaluator.class);
  private final Authentication user = mock(Authentication.class);
  private final TenantBrandingController controller =
      new TenantBrandingController(service, permissions);
  private final MockMultipartFile file =
      new MockMultipartFile("file", "logo.svg", "image/svg+xml", new byte[] {1});

  @Test
  void aReaderCannotUploadOrRemoveArtwork() {
    assertThatThrownBy(
            () -> controller.upload(BrandAssetKind.LOGO, BrandColourMode.ORIGINAL, file, user))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> controller.remove(BrandAssetKind.ICON, user))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(service);
  }

  @Test
  void theAdministratorMayManageArtwork_andReadReturnsTheActualPermission() {
    when(permissions.can(user, "members", "manage")).thenReturn(true);
    controller.upload(BrandAssetKind.LOGO, BrandColourMode.MONOCHROME, file, user);
    controller.remove(BrandAssetKind.ICON, user);
    controller.read(user);
    verify(service).upload(BrandAssetKind.LOGO, BrandColourMode.MONOCHROME, file);
    verify(service).remove(BrandAssetKind.ICON);
    verify(service).read(true);
  }
}
