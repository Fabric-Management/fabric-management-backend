package com.fabricmanagement.platform.subscription.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.subscription.domain.OSDefinition;
import com.fabricmanagement.platform.subscription.infra.repository.OSDefinitionRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationPackageQueryServiceTest {
  private final OSDefinitionRepository rows = mock(OSDefinitionRepository.class);
  private final EvaluationPackageQueryService service =
      new EvaluationPackageQueryService(rows, mock(EntityManager.class));

  @Test
  void dynamicallyOfferedPackagesAreIncludedAndInternalInactiveOrDeletedDefinitionsAreExcluded() {
    var base = definition("FabricOS");
    var offered = definition("WarehouseOS");
    var internal = definition("InternalOS");
    internal.setEvaluationAvailable(false);
    var inactive = definition("RetiredOS");
    inactive.setIsActive(false);
    var deleted = definition("ArchivedOS");
    deleted.setDeletedAt(java.time.Instant.now());
    var privateDefinition = definition("CustomerOS");
    privateDefinition.setTenantId(java.util.UUID.randomUUID());
    when(rows.findByIsActiveTrue())
        .thenReturn(List.of(base, offered, internal, inactive, deleted, privateDefinition));
    assertThat(service.catalogue().codes()).containsExactly("FabricOS", "WarehouseOS");
    service.requireActiveSelection(List.of("FabricOS", "WarehouseOS"));
    assertThatThrownBy(() -> service.requireActiveSelection(List.of("FabricOS", "InternalOS")))
        .isInstanceOf(PlatformDomainException.class);
    assertThatThrownBy(() -> service.requireActiveSelection(List.of("FabricOS", "FabricOS")))
        .isInstanceOf(PlatformDomainException.class);
  }

  @Test
  void missingBaseOrInvalidTierFailsVisiblyRatherThanInventingCatalogueData() {
    when(rows.findByIsActiveTrue()).thenReturn(List.of(definition("WarehouseOS")));
    assertThatThrownBy(service::catalogue).isInstanceOf(PlatformDomainException.class);
    var base = definition("FabricOS");
    base.setDefaultTier("Unlisted");
    when(rows.findByIsActiveTrue()).thenReturn(List.of(base));
    assertThatThrownBy(service::catalogue).isInstanceOf(PlatformDomainException.class);
  }

  @Test
  void catalogueVersionChangesWhenAnOptionIsAddedOrItsTierChanges() {
    var base = definition("FabricOS");
    when(rows.findByIsActiveTrue()).thenReturn(List.of(base));
    String first = service.catalogue().version();
    var offered = definition("WarehouseOS");
    when(rows.findByIsActiveTrue()).thenReturn(List.of(offered, base));
    String second = service.catalogue().version();
    assertThat(second).isNotEqualTo(first);
    offered.setAvailableTiers(List.of("Base", "Standard"));
    offered.setDefaultTier("Standard");
    assertThat(service.catalogue().version()).isNotEqualTo(second);
  }

  private OSDefinition definition(String code) {
    var definition =
        OSDefinition.builder()
            .osCode(code)
            .osName(code)
            .defaultTier("Base")
            .availableTiers(List.of("Base"))
            .build();
    definition.setTenantId(
        com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID);
    return definition;
  }
}
