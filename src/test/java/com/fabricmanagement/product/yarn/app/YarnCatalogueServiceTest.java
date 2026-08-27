package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.facade.PropertyRegistryFacade;
import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.reference.YarnEndUse;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.infra.repository.YarnEndUseRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnSpinningSystemRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnTestMethodRepository;
import jakarta.persistence.Column;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YarnCatalogueServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private YarnSpinningSystemRepository spinningSystemRepository;
  @Mock private YarnEndUseRepository endUseRepository;
  @Mock private YarnTestMethodRepository testMethodRepository;
  @Mock private PropertyRegistryFacade propertyRegistryFacade;

  private SpinningSystemCatalogService spinningSystemService;
  private EndUseCatalogService endUseService;
  private TestMethodCatalogService testMethodService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    spinningSystemService = new SpinningSystemCatalogService(spinningSystemRepository);
    endUseService = new EndUseCatalogService(endUseRepository);
    testMethodService = new TestMethodCatalogService(testMethodRepository, propertyRegistryFacade);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void spinningSystemRejectsMissingFamilyAndNonUpperSnakeCode() {
    assertThatThrownBy(
            () ->
                spinningSystemService.defineTenantSpinningSystem("DREF_3", "DREF-3", null, 1, null))
        .isInstanceOf(YarnDomainException.class)
        .hasMessageContaining("technologyFamily");
    assertThatThrownBy(
            () ->
                spinningSystemService.defineTenantSpinningSystem(
                    "dref_3", "DREF-3", null, 1, SpinningTechnologyFamily.FRICTION))
        .isInstanceOf(YarnDomainException.class)
        .hasMessageContaining("upper snake");
  }

  @Test
  void tenantServiceCreationAlwaysProducesTenantOwnedRows() {
    when(spinningSystemRepository.existsByTenantIdAndCode(TENANT_ID, "DREF_3")).thenReturn(false);
    when(spinningSystemRepository.save(any(YarnSpinningSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(endUseRepository.existsByTenantIdAndCode(TENANT_ID, "MEDICAL")).thenReturn(false);
    when(endUseRepository.save(any(YarnEndUse.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    YarnSpinningSystem created =
        spinningSystemService.defineTenantSpinningSystem(
            "DREF_3",
            "DREF-3",
            "House friction-spinning label",
            10,
            SpinningTechnologyFamily.FRICTION);
    YarnEndUse createdEndUse = endUseService.defineTenantEndUse("MEDICAL", "Medical", null, 20);

    assertThat(created.isSystemDefined()).isFalse();
    assertThat(created.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(createdEndUse.isSystemDefined()).isFalse();
    assertThat(createdEndUse.getTenantId()).isEqualTo(TENANT_ID);
  }

  @Test
  void semanticChangesAreRejectedWhileDisplayFieldsRemainMutable() {
    UUID id = UUID.randomUUID();
    YarnSpinningSystem stored =
        YarnSpinningSystem.defineTenant(
            TENANT_ID, "DREF_3", "DREF-3", null, 10, SpinningTechnologyFamily.FRICTION);
    stored.setId(id);
    when(spinningSystemRepository.findByIdAndTenantId(id, TENANT_ID))
        .thenReturn(Optional.of(stored));

    assertThatThrownBy(
            () ->
                spinningSystemService.update(
                    id, "DREF_3", "Renamed", "New display copy", 11, SpinningTechnologyFamily.RING))
        .isInstanceOf(YarnDomainException.class)
        .hasMessageContaining("immutable");
    verify(spinningSystemRepository, never()).save(any());
  }

  @Test
  void duplicateCodeIsRejectedWithinTheTenant() {
    when(endUseRepository.existsByTenantIdAndCode(TENANT_ID, "MEDICAL")).thenReturn(true);

    assertThatThrownBy(() -> endUseService.defineTenantEndUse("MEDICAL", "Medical", null, 20))
        .isInstanceOf(YarnDomainException.class)
        .hasMessageContaining("already exists");
    verify(endUseRepository, never()).save(any());
  }

  @Test
  void renameDescribeChangesOnlyDisplayFields() {
    UUID id = UUID.randomUUID();
    YarnEndUse stored = YarnEndUse.defineTenant(TENANT_ID, "MEDICAL", "Medical", null, 20);
    stored.setId(id);
    when(endUseRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(stored));
    when(endUseRepository.save(stored)).thenReturn(stored);

    YarnEndUse updated =
        endUseService.update(id, "MEDICAL", "Medical Textiles", "Clinical textile use", 21);

    assertThat(updated.getCode()).isEqualTo("MEDICAL");
    assertThat(updated.getName()).isEqualTo("Medical Textiles");
    assertThat(updated.getDescription()).isEqualTo("Clinical textile use");
    assertThat(updated.getDisplayOrder()).isEqualTo(21);
  }

  @Test
  void testMethodRejectsInstrumentWithoutStandard() {
    assertThatThrownBy(
            () ->
                testMethodService.defineTenantTestMethod(
                    "HOUSE_TWIST", "House Twist", null, 1, null, "Twist Tester", null))
        .isInstanceOf(YarnDomainException.class)
        .hasMessageContaining("requires standardRef");
  }

  @Test
  void testMethodApplicabilityMustResolveThroughPropertyRegistry() {
    when(propertyRegistryFacade.resolve(TENANT_ID, "UNKNOWN_PROPERTY"))
        .thenThrow(new PropertyRegistryException("missing"));

    assertThatThrownBy(
            () ->
                testMethodService.defineTenantTestMethod(
                    "HOUSE_TWIST", "House Twist", null, 1, "HOUSE 1", null, "UNKNOWN_PROPERTY"))
        .isInstanceOf(YarnDomainException.class)
        .hasMessageContaining("does not resolve");
    verify(testMethodRepository, never()).save(any());
  }

  @Test
  void unrestrictedTestMethodDoesNotInventAPropertyKey() {
    when(testMethodRepository.existsByTenantIdAndCode(TENANT_ID, "SUPPLIER_NOTE"))
        .thenReturn(false);
    when(testMethodRepository.save(any(YarnTestMethod.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    YarnTestMethod created =
        testMethodService.defineTenantTestMethod(
            "SUPPLIER_NOTE", "Supplier Note", null, 99, null, null, null);

    assertThat(created.getApplicablePropertyKey()).isNull();
    assertThat(created.isSystemDefined()).isFalse();
    verify(propertyRegistryFacade, never()).resolve(any(), any());
  }

  @Test
  void deactivationIsSoftAndTenantScoped() {
    UUID id = UUID.randomUUID();
    YarnEndUse stored = YarnEndUse.defineTenant(TENANT_ID, "MEDICAL", "Medical", null, 20);
    stored.setId(id);
    when(endUseRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(stored));
    when(endUseRepository.save(stored)).thenReturn(stored);

    endUseService.deactivate(id);

    assertThat(stored.getIsActive()).isFalse();
    assertThat(stored.getDeletedAt()).isNotNull();
    verify(endUseRepository).save(stored);
  }

  @Test
  void allSemanticJpaMappingsAreNonUpdatable() throws NoSuchFieldException {
    assertNonUpdatable(YarnSpinningSystem.class, "code", "technologyFamily", "systemDefined");
    assertNonUpdatable(YarnEndUse.class, "code", "systemDefined");
    assertNonUpdatable(
        YarnTestMethod.class,
        "code",
        "standardRef",
        "instrument",
        "applicablePropertyKey",
        "systemDefined");
  }

  private static void assertNonUpdatable(Class<?> type, String... fieldNames)
      throws NoSuchFieldException {
    for (String fieldName : fieldNames) {
      Column column = type.getDeclaredField(fieldName).getAnnotation(Column.class);
      assertThat(column)
          .as("%s.%s must carry @Column", type.getSimpleName(), fieldName)
          .isNotNull();
      assertThat(column.updatable())
          .as("%s.%s must be immutable in JPA", type.getSimpleName(), fieldName)
          .isFalse();
    }
  }
}
