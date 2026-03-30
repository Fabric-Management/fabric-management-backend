package com.fabricmanagement.production.masterdata.material.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.ai.AIQueryNormalizer;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaterialAIToolProviderTest {

  @Mock private MaterialFacade materialFacade;
  @Mock private FiberFacade fiberFacade;
  @Mock private AIQueryNormalizer queryNormalizer;

  @InjectMocks private MaterialAIToolProvider materialAIToolProvider;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
  }

  @Test
  @DisplayName("Should support all material tools")
  void shouldSupportMaterialTools() {
    assertThat(materialAIToolProvider.getSupportedTools())
        .containsExactlyInAnyOrder(
            "check_material_stock", "create_material", "search_materials", "get_production_status");
  }

  @Test
  @DisplayName("Should check material stock")
  void shouldCheckMaterialStock() {
    // Given
    String materialName = "Cotton";
    MaterialDto material =
        MaterialDto.builder()
            .uid(materialName)
            .materialType(MaterialType.FIBER)
            .unit("kg")
            .isActive(true)
            .build();
    when(materialFacade.findByTenant(tenantId)).thenReturn(List.of(material));

    // When
    String result =
        materialAIToolProvider.execute(
            tenantId, "check_material_stock", Map.of("materialName", materialName));

    // Then
    assertThat(result).contains("Cotton").contains("FIBER").contains("Active");
  }

  @Test
  @DisplayName("Should search materials with fiber cross-reference")
  void shouldSearchMaterialsWithFiberCrossReference() {
    // Given
    String query = "Organic Cotton";
    UUID materialId = UUID.randomUUID();
    MaterialDto material =
        MaterialDto.builder()
            .id(materialId)
            .uid("MAT-001")
            .materialType(MaterialType.FIBER)
            .isActive(true)
            .build();
    FiberDto fiber =
        FiberDto.builder()
            .materialId(materialId)
            .fiberName(query)
            .uid("FIB-001")
            .status(FiberStatus.ACTIVE)
            .build();

    when(materialFacade.findByTenant(tenantId)).thenReturn(List.of(material));
    when(queryNormalizer.normalizeFiberQuery(anyString())).thenReturn(query);
    when(fiberFacade.findByMaterialIds(any())).thenReturn(List.of(fiber));

    // When
    String result =
        materialAIToolProvider.execute(tenantId, "search_materials", Map.of("query", query));

    // Then
    assertThat(result).contains("Found 1 materials").contains("MAT-001");
    verify(fiberFacade).findByMaterialIds(any());
  }

  @Test
  @DisplayName("Should provide production status summary")
  void shouldProvideProductionStatus() {
    // Given
    MaterialDto mat1 =
        MaterialDto.builder().materialType(MaterialType.FIBER).isActive(true).build();
    MaterialDto mat2 = MaterialDto.builder().materialType(MaterialType.YARN).isActive(true).build();
    when(materialFacade.findByTenant(tenantId)).thenReturn(List.of(mat1, mat2));

    // When
    String result = materialAIToolProvider.execute(tenantId, "get_production_status", Map.of());

    // Then
    assertThat(result).contains("Production Status Summary").contains("Active Materials: 2");
    assertThat(result).contains("FIBER: 1").contains("YARN: 1");
  }

  @Test
  @DisplayName("Should create material successfully")
  void shouldCreateMaterial() {
    // Given
    Map<String, Object> params = Map.of("materialType", "FIBER", "unit", "kg");
    MaterialDto created =
        MaterialDto.builder()
            .id(UUID.randomUUID())
            .uid("MAT-001")
            .materialType(MaterialType.FIBER)
            .unit("kg")
            .build();
    when(materialFacade.createMaterial(any(CreateMaterialRequest.class))).thenReturn(created);

    // When
    String result = materialAIToolProvider.execute(tenantId, "create_material", params);

    // Then
    assertThat(result).contains("Material created successfully!").contains("MAT-001");
  }
}
