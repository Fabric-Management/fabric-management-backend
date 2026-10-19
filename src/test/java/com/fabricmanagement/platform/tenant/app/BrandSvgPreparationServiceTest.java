package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.platform.tenant.domain.BrandColourMode;
import com.fabricmanagement.platform.tenant.domain.BrandSvgOutlinePort;
import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BrandSvgPreparationServiceTest {
  private final BrandSvgSanitizerService sanitizer = new BrandSvgSanitizerService();
  private final BrandSvgOutlinePort outliner = mock(BrandSvgOutlinePort.class);
  private final BrandSvgPreparationService service =
      new BrandSvgPreparationService(sanitizer, outliner);
  private static final String OPEN =
      "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'>";
  private static final String TEXT =
      OPEN
          + "<text style=\"font-family:'Brand Font';font-weight:900;font-size:20px;fill:#fff\""
          + " x='0' y='50'>Brand</text></svg>";
  private static final String PATH =
      OPEN + "<path style='font-size:20px;fill:#fff' d='M0 0L10 10Z'/></svg>";

  @Test
  void outlinesTextAndRevalidatesTheResultWithoutPersistingAnything() {
    when(outliner.outline(anyString())).thenReturn(PATH);
    String prepared =
        new String(service.prepare(file(TEXT), BrandColourMode.MONOCHROME), StandardCharsets.UTF_8);
    assertThat(prepared).contains("path").doesNotContain("<text", "font-size");
    verify(outliner).outline(contains("font-family"));
    assertThat(sanitizer.sanitize(prepared.getBytes(StandardCharsets.UTF_8))).contains("path");
  }

  @Test
  void inkscapeCanvasStateDoesNotPreventTextFromReachingTheFontCheck() {
    when(outliner.outline(anyString()))
        .thenThrow(
            new TenantBrandingDomainException(
                "Missing font", "TENANT_BRANDING_FONT_UNAVAILABLE", 422));
    String exported =
        TEXT.replace(
                "<svg ",
                "<svg xmlns:sodipodi='http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd' ")
            .replace("<text ", "<sodipodi:namedview/><text ");
    assertThatThrownBy(() -> service.prepare(file(exported), BrandColourMode.ORIGINAL))
        .isInstanceOf(TenantBrandingDomainException.class)
        .hasMessage("Missing font");
    verify(outliner).outline(argThat(svg -> !svg.contains("namedview")));
  }

  @Test
  void vectorArtworkDoesNotRequireAnInstalledConverter() {
    String svg = OPEN + "<path fill='#fff' d='M0 0L10 10Z'/></svg>";
    service.prepare(file(svg), BrandColourMode.ORIGINAL);
    verifyNoInteractions(outliner);
  }

  @Test
  void missingFontsAreReportedAndNeverSubstituted() {
    when(outliner.outline(anyString()))
        .thenThrow(
            new TenantBrandingDomainException(
                "Missing font", "TENANT_BRANDING_FONT_UNAVAILABLE", 422));
    assertThatThrownBy(() -> service.prepare(file(TEXT), BrandColourMode.ORIGINAL))
        .isInstanceOf(TenantBrandingDomainException.class)
        .hasMessage("Missing font");
  }

  @Test
  void unsafeInputNeverReachesTheWorkerAndUnsafeOutputNeverReachesTheBrowser() {
    assertThatThrownBy(
            () -> service.prepare(file(OPEN + "<script/></svg>"), BrandColourMode.ORIGINAL))
        .isInstanceOf(TenantBrandingDomainException.class);
    verifyNoInteractions(outliner);
    when(outliner.outline(anyString()))
        .thenReturn(OPEN + "<image href='https://bad/image'/></svg>");
    assertThatThrownBy(() -> service.prepare(file(TEXT), BrandColourMode.ORIGINAL))
        .isInstanceOf(TenantBrandingDomainException.class);
  }

  @Test
  void ordinaryUploadsStillRejectTextAndExternalStylesCannotReachConversion() {
    assertThatThrownBy(() -> sanitizer.sanitize(TEXT.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(TenantBrandingDomainException.class);
    assertThatThrownBy(
            () ->
                service.prepare(
                    file(OPEN + "<text style='font-family:url(https://bad)'>B</text></svg>"),
                    BrandColourMode.ORIGINAL))
        .isInstanceOf(TenantBrandingDomainException.class);
    verifyNoInteractions(outliner);
  }

  @Test
  void unsupportedTextLengthNeverSilentlyChangesTheLogo() {
    assertThatThrownBy(
            () ->
                service.prepare(
                    file(
                        OPEN + "<text font-family='Brand Font' textLength='20'>Brand</text></svg>"),
                    BrandColourMode.ORIGINAL))
        .isInstanceOf(TenantBrandingDomainException.class);
    verifyNoInteractions(outliner);
  }

  private MockMultipartFile file(String svg) {
    return new MockMultipartFile(
        "file", "logo.svg", "image/svg+xml", svg.getBytes(StandardCharsets.UTF_8));
  }
}
