package com.fabricmanagement.production.masterdata.color.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fabricmanagement.production.masterdata.color.domain.exception.ColorDomainException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cross-field rules of the shade standard. The DB mirrors each of these as a CHECK constraint. */
class ColorTest {

  private final UUID tenantId = UUID.randomUUID();

  private ColorCardSpec.ColorCardSpecBuilder spec() {
    return ColorCardSpec.builder().code("NAVY-01").name("Navy");
  }

  private ColorCardSpec.ColorCardSpecBuilder specWithTargetLab() {
    return spec()
        .targetLabL(new BigDecimal("22.10"))
        .targetLabA(new BigDecimal("3.40"))
        .targetLabB(new BigDecimal("-18.90"))
        .targetLabIlluminant(LabIlluminant.D65)
        .targetLabObserver(LabObserver.DEG_10);
  }

  @Test
  @DisplayName("Should default an unspecified card to DYED / UNDEFINED / DRAFT")
  void shouldDefaultToDyedUndefinedDraft() {
    Color color = Color.create(tenantId, ColorCardSpec.basic("navy-01", "Navy", "#001f3f"));

    assertEquals(ColorType.DYED, color.getColorType());
    assertEquals(ColorFamily.UNDEFINED, color.getColorFamily());
    assertEquals(ColorStandardStatus.DRAFT, color.getStandardStatus());
  }

  @Test
  @DisplayName("Should default Pantone carrier to TCX when only a code is given")
  void shouldDefaultPantoneSystemToTcx() {
    Color color = Color.create(tenantId, spec().pantoneCode(" 19-4024 ").build());

    assertEquals("19-4024", color.getPantoneCode());
    assertEquals(PantoneSystem.TCX, color.getPantoneSystem());
  }

  @Test
  @DisplayName("Should reject a Pantone carrier without a Pantone code")
  void shouldRejectPantoneSystemWithoutCode() {
    ColorDomainException ex =
        assertThrows(
            ColorDomainException.class,
            () -> Color.create(tenantId, spec().pantoneSystem(PantoneSystem.TCX).build()));

    assertEquals("PRODUCTION_COLOR_INVALID", ex.getErrorCode());
    assertEquals(422, ex.getHttpStatus());
  }

  @Test
  @DisplayName("Should reject a partial target Lab measurement")
  void shouldRejectPartialTargetLab() {
    assertThrows(
        ColorDomainException.class,
        () ->
            Color.create(
                tenantId,
                spec()
                    .targetLabL(new BigDecimal("22.10"))
                    .targetLabA(new BigDecimal("3.40"))
                    .build()));
  }

  @Test
  @DisplayName("Should accept a complete target Lab measurement")
  void shouldAcceptCompleteTargetLab() {
    Color color = Color.create(tenantId, specWithTargetLab().build());

    assertEquals(LabIlluminant.D65, color.getTargetLabIlluminant());
    assertEquals(LabObserver.DEG_10, color.getTargetLabObserver());
  }

  @Test
  @DisplayName("Should reject a target Lab lightness outside 0..100")
  void shouldRejectTargetLabLightnessOutOfRange() {
    assertThrows(
        ColorDomainException.class,
        () ->
            Color.create(tenantId, specWithTargetLab().targetLabL(new BigDecimal("140")).build()));
  }

  @Test
  @DisplayName("Should default the Delta-E formula to the textile CMC(2:1)")
  void shouldDefaultDeltaEFormulaToCmc() {
    Color color =
        Color.create(tenantId, specWithTargetLab().deltaETolerance(new BigDecimal("1.20")).build());

    assertEquals(DeltaEFormula.CMC_2_1, color.getDeltaEFormula());
  }

  @Test
  @DisplayName("Should reject a Delta-E formula without a tolerance")
  void shouldRejectDeltaEFormulaWithoutTolerance() {
    assertThrows(
        ColorDomainException.class,
        () -> Color.create(tenantId, spec().deltaEFormula(DeltaEFormula.CIEDE2000).build()));
  }

  @Test
  @DisplayName("Should reject a non-positive Delta-E tolerance")
  void shouldRejectNonPositiveDeltaETolerance() {
    assertThrows(
        ColorDomainException.class,
        () -> Color.create(tenantId, specWithTargetLab().deltaETolerance(BigDecimal.ZERO).build()));
  }

  @Test
  @DisplayName("Should reject a Delta-E tolerance with nothing to measure against")
  void shouldRejectDeltaEToleranceWithoutTarget() {
    ColorDomainException ex =
        assertThrows(
            ColorDomainException.class,
            () -> Color.create(tenantId, spec().deltaETolerance(new BigDecimal("1.20")).build()));

    assertEquals("PRODUCTION_COLOR_INVALID", ex.getErrorCode());
  }

  @Test
  @DisplayName("Should accept a Delta-E tolerance targeting a Pantone reference without Lab")
  void shouldAcceptDeltaEToleranceAgainstPantone() {
    Color color =
        Color.create(
            tenantId,
            spec().pantoneCode("19-4024").deltaETolerance(new BigDecimal("1.20")).build());

    assertEquals(new BigDecimal("1.20"), color.getDeltaETolerance());
    assertNull(color.getTargetLabL());
  }

  @Test
  @DisplayName("Should reject a shade standard on an undyed card")
  void shouldRejectShadeStandardOnUndyedCard() {
    ColorDomainException ex =
        assertThrows(
            ColorDomainException.class,
            () ->
                Color.create(
                    tenantId,
                    ColorCardSpec.builder()
                        .code("PFD-00")
                        .name("Prepared for dyeing")
                        .colorType(ColorType.PFD)
                        .colorHex("#FFFFFF")
                        .build()));

    assertEquals("PRODUCTION_COLOR_INVALID", ex.getErrorCode());
  }

  @Test
  @DisplayName("Should accept an undyed card with no shade standard")
  void shouldAcceptUndyedCardWithoutStandard() {
    Color color =
        Color.create(
            tenantId,
            ColorCardSpec.builder()
                .code("greige-00")
                .name("Greige")
                .colorType(ColorType.GREIGE)
                .build());

    assertEquals("GREIGE-00", color.getCode());
    assertNull(color.getColorHex());
    assertNull(color.getPantoneCode());
  }

  @Test
  @DisplayName("Should clear cleared fields on update")
  void shouldClearFieldsOnUpdate() {
    Color color = Color.create(tenantId, spec().pantoneCode("19-4024").notes("first").build());

    color.update(ColorCardSpec.basic("NAVY-01", "Navy", null));

    assertNull(color.getPantoneCode());
    assertNull(color.getPantoneSystem());
    assertNull(color.getNotes());
  }

  @Test
  @DisplayName("Should refuse to approve a standard with no target")
  void shouldRefuseToApproveStandardWithoutTarget() {
    Color color = Color.create(tenantId, ColorCardSpec.basic("NAVY-01", "Navy", "#001F3F"));

    ColorDomainException ex = assertThrows(ColorDomainException.class, color::approve);

    assertEquals("PRODUCTION_COLOR_INVALID", ex.getErrorCode());
  }

  @Test
  @DisplayName("Should approve an undyed card, which has no target by definition")
  void shouldApproveUndyedCard() {
    Color color =
        Color.create(
            tenantId,
            ColorCardSpec.builder().code("PFD-00").name("PFD").colorType(ColorType.PFD).build());

    assertDoesNotThrow(color::approve);
    assertEquals(ColorStandardStatus.APPROVED, color.getStandardStatus());
  }

  @Test
  @DisplayName("Should freeze standard-defining fields once approved")
  void shouldFreezeStandardOnceApproved() {
    Color color = Color.create(tenantId, specWithTargetLab().build());
    color.approve();

    ColorDomainException ex =
        assertThrows(
            ColorDomainException.class,
            () -> color.update(specWithTargetLab().targetLabL(new BigDecimal("30.00")).build()));

    assertEquals("PRODUCTION_COLOR_STANDARD_APPROVED", ex.getErrorCode());
    assertEquals(409, ex.getHttpStatus());
  }

  @Test
  @DisplayName("Should still allow name, notes, family and hex edits while approved")
  void shouldAllowNonStandardEditsWhileApproved() {
    Color color = Color.create(tenantId, specWithTargetLab().build());
    color.approve();

    color.update(
        specWithTargetLab()
            .name("Navy Blue")
            .notes("bulk standard")
            .colorFamily(ColorFamily.BLUE)
            .colorHex("#1F2A44")
            .build());

    assertEquals("Navy Blue", color.getName());
    assertEquals(ColorFamily.BLUE, color.getColorFamily());
    assertEquals(ColorStandardStatus.APPROVED, color.getStandardStatus());
  }

  @Test
  @DisplayName("Should treat an unchanged tolerance scale as unchanged")
  void shouldTreatEquivalentToleranceScalesAsUnchanged() {
    Color color =
        Color.create(tenantId, specWithTargetLab().deltaETolerance(new BigDecimal("1.2")).build());
    color.approve();

    assertDoesNotThrow(
        () -> color.update(specWithTargetLab().deltaETolerance(new BigDecimal("1.20")).build()));
  }

  @Test
  @DisplayName("Should allow the standard to change after reverting to draft")
  void shouldAllowStandardChangeAfterRevert() {
    Color color = Color.create(tenantId, specWithTargetLab().build());
    color.approve();
    color.revertToDraft();

    color.update(specWithTargetLab().targetLabL(new BigDecimal("30.00")).build());

    assertEquals(0, new BigDecimal("30.00").compareTo(color.getTargetLabL()));
    assertEquals(ColorStandardStatus.DRAFT, color.getStandardStatus());
  }
}
