package com.fabricmanagement.production.masterdata.color.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorCardSpec;
import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorStandardStatus;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.DeltaEFormula;
import com.fabricmanagement.production.masterdata.color.domain.LabIlluminant;
import com.fabricmanagement.production.masterdata.color.domain.LabObserver;
import com.fabricmanagement.production.masterdata.color.domain.PantoneSystem;
import com.fabricmanagement.production.masterdata.color.dto.ColorDto;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ColorMapperTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private final ColorMapper mapper = Mappers.getMapper(ColorMapper.class);

  @Test
  void mapsEveryFieldOfFullyPopulatedCard() {
    UUID colorId = UUID.fromString("22222222-2222-4222-8222-222222222222");
    Color color =
        Color.create(
            TENANT_ID,
            ColorCardSpec.builder()
                .code(" navy-01 ")
                .name(" Deep Navy ")
                .colorHex("#1f2a44")
                .colorType(ColorType.YARN_DYED)
                .pantoneCode(" 19-4024 ")
                .pantoneSystem(PantoneSystem.TCX)
                .colorFamily(ColorFamily.BLUE)
                .targetLabL(new BigDecimal("20.25"))
                .targetLabA(new BigDecimal("1.50"))
                .targetLabB(new BigDecimal("-18.75"))
                .targetLabIlluminant(LabIlluminant.D65)
                .targetLabObserver(LabObserver.DEG_10)
                .deltaETolerance(new BigDecimal("1.25"))
                .deltaEFormula(DeltaEFormula.CIEDE2000)
                .notes(" Contract standard ")
                .build());
    color.setId(colorId);
    color.approve();
    color.delete();

    ColorDto dto = mapper.toDto(color);

    assertThat(dto.id()).isEqualTo(colorId);
    assertThat(dto.code()).isEqualTo("NAVY-01");
    assertThat(dto.name()).isEqualTo("Deep Navy");
    assertThat(dto.colorHex()).isEqualTo("#1F2A44");
    assertThat(dto.colorType()).isEqualTo(ColorType.YARN_DYED);
    assertThat(dto.pantoneCode()).isEqualTo("19-4024");
    assertThat(dto.pantoneSystem()).isEqualTo(PantoneSystem.TCX);
    assertThat(dto.pantoneLabel()).isEqualTo("19-4024 TCX");
    assertThat(dto.colorFamily()).isEqualTo(ColorFamily.BLUE);
    assertThat(dto.targetLabL()).isEqualByComparingTo("20.25");
    assertThat(dto.targetLabA()).isEqualByComparingTo("1.50");
    assertThat(dto.targetLabB()).isEqualByComparingTo("-18.75");
    assertThat(dto.targetLabIlluminant()).isEqualTo(LabIlluminant.D65);
    assertThat(dto.targetLabObserver()).isEqualTo(LabObserver.DEG_10);
    assertThat(dto.deltaETolerance()).isEqualByComparingTo("1.25");
    assertThat(dto.deltaEFormula()).isEqualTo(DeltaEFormula.CIEDE2000);
    assertThat(dto.standardStatus()).isEqualTo(ColorStandardStatus.APPROVED);
    assertThat(dto.notes()).isEqualTo("Contract standard");
    assertThat(dto.active()).isFalse();
  }

  @Test
  void mapsMinimalCardAndTreatsNullActiveFlagAsFalse() {
    Color color = Color.create(TENANT_ID, "RAW-01", "Raw", null);
    color.setIsActive(null);

    ColorDto dto = mapper.toDto(color);

    assertThat(dto.id()).isNull();
    assertThat(dto.code()).isEqualTo("RAW-01");
    assertThat(dto.name()).isEqualTo("Raw");
    assertThat(dto.colorHex()).isNull();
    assertThat(dto.colorType()).isEqualTo(ColorType.DYED);
    assertThat(dto.pantoneCode()).isNull();
    assertThat(dto.pantoneSystem()).isNull();
    assertThat(dto.pantoneLabel()).isNull();
    assertThat(dto.colorFamily()).isEqualTo(ColorFamily.UNDEFINED);
    assertThat(dto.targetLabL()).isNull();
    assertThat(dto.targetLabA()).isNull();
    assertThat(dto.targetLabB()).isNull();
    assertThat(dto.targetLabIlluminant()).isNull();
    assertThat(dto.targetLabObserver()).isNull();
    assertThat(dto.deltaETolerance()).isNull();
    assertThat(dto.deltaEFormula()).isNull();
    assertThat(dto.standardStatus()).isEqualTo(ColorStandardStatus.DRAFT);
    assertThat(dto.notes()).isNull();
    assertThat(dto.active()).isFalse();
  }
}
