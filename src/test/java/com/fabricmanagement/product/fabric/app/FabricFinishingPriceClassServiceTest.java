package com.fabricmanagement.product.fabric.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingPriceClassRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceClassDto;
import com.fabricmanagement.product.fabric.dto.UpdateFabricFinishingPriceClassRequest;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceClassRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** FABRIC-3c: the tenant's price classes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FabricFinishingPriceClassServiceTest {

  private static final UUID TENANT = UUID.randomUUID();

  @Mock private FabricFinishingPriceClassRepository repository;

  private FabricFinishingPriceClassService service;
  private FabricFinishingPriceClass light;
  private FabricFinishingPriceClass dark;
  private FabricFinishingPriceClass black;
  private FabricFinishingPriceClass retired;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service = new FabricFinishingPriceClassService(repository);
    light = tier("LIGHT", "Light", 1);
    dark = tier("DARK", "Dark", 3);
    black = tier("BLACK", "Black", null);
    retired = tier("OLD_TIER", "Old tier", 2);
    retired.update("Old tier", null, 2, false);
    when(repository.findByTenantIdAndDeletedAtIsNull(TENANT))
        .thenReturn(List.of(black, dark, retired, light));
    when(repository.save(any(FabricFinishingPriceClass.class)))
        .thenAnswer(call -> call.getArgument(0));
    when(repository.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, dark.getId()))
        .thenReturn(Optional.of(dark));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static FabricFinishingPriceClass tier(String code, String name, Integer order) {
    FabricFinishingPriceClass tier =
        FabricFinishingPriceClass.defineSystem(TENANT, code, name, null, order);
    ReflectionTestUtils.setField(tier, "id", UUID.randomUUID());
    return tier;
  }

  @Test
  void theListIsInDisplayOrderWithUnorderedLastAndHidesRetiredClassesByDefault() {
    assertThat(service.list(false))
        .extracting(FabricFinishingPriceClassDto::code)
        .containsExactly("LIGHT", "DARK", "BLACK");
  }

  @Test
  void retiredClassesCanBeListedToo() {
    assertThat(service.list(true))
        .extracting(FabricFinishingPriceClassDto::code)
        .containsExactly("LIGHT", "OLD_TIER", "DARK", "BLACK");
    assertThat(service.list(true))
        .filteredOn(dto -> dto.code().equals("OLD_TIER"))
        .singleElement()
        .satisfies(dto -> assertThat(dto.active()).isFalse());
  }

  @Test
  void aNewClassIsTenantDefined() {
    FabricFinishingPriceClassDto created =
        service.create(
            new CreateFabricFinishingPriceClassRequest("NAVY_DEEP", "Deep navy", null, 8));

    assertThat(created.code()).isEqualTo("NAVY_DEEP");
    assertThat(created.systemDefined()).isFalse();
    assertThat(created.active()).isTrue();
  }

  @Test
  void aCodeTheTenantAlreadyHasIsRefusedEvenWhenTheClassIsRetired() {
    when(repository.existsByTenantIdAndCode(TENANT, "OLD_TIER")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateFabricFinishingPriceClassRequest(
                        "OLD_TIER", "Old again", null, null)))
        .isInstanceOfSatisfying(
            DomainException.class,
            problem -> {
              assertThat(problem.getErrorCode()).isEqualTo("FINISHING_PRICE_CLASS_DUPLICATE");
              assertThat(problem.getHttpStatus()).isEqualTo(409);
            });
  }

  @Test
  void updatingRenamesAndRetiresButNeverChangesTheCode() {
    FabricFinishingPriceClassDto updated =
        service.update(
            dark.getId(),
            new UpdateFabricFinishingPriceClassRequest("Deep shades", "High dye %", 4, false));

    assertThat(updated.code()).isEqualTo("DARK");
    assertThat(updated.name()).isEqualTo("Deep shades");
    assertThat(updated.displayOrder()).isEqualTo(4);
    assertThat(updated.active()).isFalse();
    assertThat(updated.systemDefined()).isTrue();
  }

  @Test
  void anUnknownClassIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(repository.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, unknown))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.update(
                    unknown, new UpdateFabricFinishingPriceClassRequest("X", null, null, true)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.require(unknown)).isInstanceOf(NotFoundException.class);
  }
}
