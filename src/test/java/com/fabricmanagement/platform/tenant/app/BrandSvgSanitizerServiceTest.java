package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.platform.tenant.domain.BrandAppearance;
import com.fabricmanagement.platform.tenant.domain.TenantBrandAsset;
import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BrandSvgSanitizerServiceTest {
  private final BrandSvgSanitizerService sanitizer = new BrandSvgSanitizerService();
  private static final String OPEN =
      "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'>";

  @Test
  void acceptsIllustratorClassesAndInlinePaints_butRemovesMetadataAndClasses() {
    String svg =
        sanitize(
            OPEN
                + "<metadata><anything>Designer metadata</anything></metadata>"
                + "<style>.st0 { fill: #fff; stroke: #fff; stroke-width: 2; }</style>"
                + "<path class='st0' style='fill:none' d='M0 0 L10 10'/></svg>");
    assertThat(svg)
        .contains("fill=\"none\"", "stroke=\"#fff\"", "viewBox=\"0 0 100 100\"")
        .doesNotContain("metadata", "Designer metadata", "class=", "<style");
  }

  @Test
  void dropsInkscapeCanvasStateWithoutChangingTheArtwork() {
    String svg =
        sanitize(
            OPEN.replace(
                    "<svg ",
                    "<svg xmlns:sodipodi='http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd' "
                        + "xmlns:inkscape='http://www.inkscape.org/namespaces/inkscape' ")
                + "<sodipodi:namedview pagecolor='#fff' inkscape:window-x='1680'>"
                + "<inkscape:page width='100' height='100'/></sodipodi:namedview>"
                + "<path fill='#fff' d='M0 0 L10 10'/></svg>");
    assertThat(svg)
        .contains("<path", "fill=\"#fff\"", "d=\"M0 0 L10 10\"")
        .doesNotContain("namedview", "window-x", "inkscape:page", "pagecolor");
  }

  @Test
  void singleColourArtwork_adaptsItsFillAndStroke_withoutFillingHollowPaths() {
    String svg =
        sanitize(
            OPEN
                + "<path fill='none' stroke='#fff' d='M0 0 L10 10'/>"
                + "<path fill='#fff' fill-opacity='0.5' d='M0 0 L20 20'/></svg>");
    assertThat(sanitizer.monochrome(svg, BrandAppearance.LIGHT))
        .contains("stroke=\"#111111\"", "fill=\"none\"", "fill-opacity=\"0.5\"");
    assertThat(sanitizer.monochrome(svg, BrandAppearance.DARK)).contains("stroke=\"#ffffff\"");
    assertThat(sanitizer.monochrome(svg, BrandAppearance.ORIGINAL)).isEqualTo(svg);
  }

  @Test
  void keepsRootFillNoneAndPaintTransparency_whenAdaptingAnOutlineIcon() {
    String svg =
        sanitize(
            OPEN.replace("viewBox=", "fill='none' viewBox=")
                + "<path stroke='rgba(255,255,255,0.5)' d='M0 0 L10 10'/></svg>");
    assertThat(sanitizer.monochrome(svg, BrandAppearance.LIGHT))
        .contains("fill=\"none\"", "stroke=\"rgba(17,17,17,0.5)\"");
  }

  @Test
  void maskedArtworkKeepsItsOriginalColours_insteadOfChangingMaskSemantics() {
    String svg =
        sanitize(
            OPEN
                + "<defs><mask id='m'><rect width='100' height='100' fill='white'/></mask>"
                + "</defs><rect width='100' height='100' mask='url(#m)'/></svg>");
    assertThat(sanitizer.monochrome(svg, BrandAppearance.ORIGINAL)).isEqualTo(svg);
    assertThatThrownBy(() -> sanitizer.monochrome(svg, BrandAppearance.LIGHT))
        .isInstanceOf(TenantBrandingDomainException.class);
  }

  @Test
  void keepsLocalGradientReferences_andColourStops() {
    String svg =
        sanitize(
            OPEN
                + "<defs><linearGradient id='a'><stop offset='0' stop-color='#123456'/>"
                + "</linearGradient></defs><rect width='100' height='100' fill='url(#a)'/></svg>");
    assertThat(svg).contains("url(#a)", "#123456");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<script>alert(1)</script>",
        "<namedview/>",
        "<namedview xmlns='urn:untrusted'><path/></namedview>",
        "<foreignObject><div>HTML</div></foreignObject>",
        "<image href='data:image/png;base64,AAAA'/>",
        "<path onload='alert(1)' d='M0 0'/>",
        "<path fill='url(https://example.com/a)' d='M0 0'/>",
        "<path style='fill:url(//example.com/a)' d='M0 0'/>",
        "<path fill='url(&#104;ttps://example.com/a)' d='M0 0'/>",
        "<linearGradient href='https://example.com/a'/>",
        "<style>@import 'https://example.com/a';</style>",
        "<style>.a { fill:var(--external); }</style><path class='a' d='M0 0'/>",
        "<animate attributeName='href' values='https://example.com'/>",
        "<text x='0' y='20'>Unembedded font</text>"
      })
  void rejectsActiveOrExternalContent(String unsafe) {
    assertThatThrownBy(() -> sanitize(OPEN + unsafe + "</svg>"))
        .isInstanceOf(TenantBrandingDomainException.class);
  }

  @Test
  void rejectsDtdsAndOversizedInput() {
    assertThatThrownBy(
            () ->
                sanitize(
                    "<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"
                        + OPEN
                        + "<title>&x;</title></svg>"))
        .isInstanceOf(TenantBrandingDomainException.class);
    assertThatThrownBy(() -> sanitizer.sanitize(new byte[TenantBrandAsset.MAX_BYTES + 1]))
        .isInstanceOf(TenantBrandingDomainException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0 0 0 10", "0 0 -10 10", "0 0 NaN 10", "0 0 Infinity 10", ""})
  void requiresAUsableAspectRatio(String viewBox) {
    assertThatThrownBy(() -> sanitize(OPEN.replace("0 0 100 100", viewBox) + "</svg>"))
        .isInstanceOf(TenantBrandingDomainException.class);
  }

  private String sanitize(String svg) {
    return sanitizer.sanitize(svg.getBytes(StandardCharsets.UTF_8));
  }
}
