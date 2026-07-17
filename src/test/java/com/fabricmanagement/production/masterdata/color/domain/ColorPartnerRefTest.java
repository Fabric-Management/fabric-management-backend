package com.fabricmanagement.production.masterdata.color.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.masterdata.color.domain.exception.ColorPartnerRefDomainException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColorPartnerRefTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID COLOR_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID PARTNER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  @Test
  void creationNormalizesTheFirstCodeAndMakesItPrimary() {
    ColorPartnerRef ref = ref(" ss26-nvy-07 ");

    assertThat(ref.getCodes()).hasSize(1);
    assertThat(ref.primaryCode().getExternalCode()).isEqualTo("ss26-nvy-07");
    assertThat(ref.primaryCode().getExternalCodeKey()).isEqualTo("SS26-NVY-07");
    assertThat(ref.primaryCode().isPrimary()).isTrue();
    assertThat(ref.primaryCode().getIsActive()).isTrue();
  }

  @Test
  void duplicateActiveKeyIsRejectedInsideTheAggregate() {
    ColorPartnerRef ref = ref("SS26-NVY-07");

    assertThatThrownBy(() -> ref.addCode(" ss26-nvy-07 ", null))
        .isInstanceOf(ColorPartnerRefDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void activePrimaryCannotBeDeactivatedDirectly() {
    ColorPartnerRef ref = ref("PRIMARY");
    ColorPartnerCode primary = ref.primaryCode();
    primary.setId(UUID.randomUUID());

    assertThatThrownBy(() -> ref.deactivateCode(primary.getId()))
        .isInstanceOf(ColorPartnerRefDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void primarySwitchUsesPreparedTargetAndLeavesExactlyOnePrimary() {
    ColorPartnerRef ref = ref("PRIMARY");
    ColorPartnerCode oldPrimary = ref.primaryCode();
    oldPrimary.setId(UUID.randomUUID());
    ColorPartnerCode alias = ref.addCode("ALIAS", "Season alias");
    alias.setId(UUID.randomUUID());

    ref.preparePrimarySwitch(alias.getId());
    assertThat(ref.getCodes()).noneMatch(ColorPartnerCode::isPrimary);
    ref.completePrimarySwitch(alias.getId());

    assertThat(ref.primaryCode()).isSameAs(alias);
    assertThat(oldPrimary.isPrimary()).isFalse();
    assertThat(ref.getCodes().stream().filter(ColorPartnerCode::isPrimary)).hasSize(1);
  }

  @Test
  void completingAnUnpreparedPrimarySwitchIsRejected() {
    ColorPartnerRef ref = ref("PRIMARY");
    ColorPartnerCode primary = ref.primaryCode();
    primary.setId(UUID.randomUUID());

    assertThatThrownBy(() -> ref.completePrimarySwitch(primary.getId()))
        .isInstanceOf(ColorPartnerRefDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void deactivationCascadesAndInactiveAggregateRejectsEveryOtherMutation() {
    ColorPartnerRef ref = ref("PRIMARY");
    ColorPartnerCode alias = ref.addCode("ALIAS", null);
    alias.setId(UUID.randomUUID());

    ref.deactivate();

    assertThat(ref.getIsActive()).isFalse();
    assertThat(ref.getCodes()).allMatch(code -> !Boolean.TRUE.equals(code.getIsActive()));
    assertThatThrownBy(() -> ref.addCode("NEW", null))
        .isInstanceOf(ColorPartnerRefDomainException.class);
    assertThatThrownBy(() -> ref.updateTolerance(BigDecimal.ONE))
        .isInstanceOf(ColorPartnerRefDomainException.class);
    assertThatThrownBy(ref::deactivate).isInstanceOf(ColorPartnerRefDomainException.class);
  }

  @Test
  void existingCodeReactivationRevivesOnlyTheSelectedPrimary() {
    ColorPartnerRef ref = ref("OLD-PRIMARY");
    ColorPartnerCode oldPrimary = ref.primaryCode();
    oldPrimary.setId(UUID.randomUUID());
    ColorPartnerCode alias = ref.addCode("SELECTED", null);
    alias.setId(UUID.randomUUID());
    ref.deactivate();

    ref.reactivateWithExistingCode(alias.getId());

    assertThat(ref.getIsActive()).isTrue();
    assertThat(alias.getIsActive()).isTrue();
    assertThat(alias.isPrimary()).isTrue();
    assertThat(oldPrimary.getIsActive()).isFalse();
    assertThat(oldPrimary.isPrimary()).isFalse();
  }

  @Test
  void newCodeReactivationDoesNotReviveOldAliases() {
    ColorPartnerRef ref = ref("OLD-PRIMARY");
    ColorPartnerCode oldPrimary = ref.primaryCode();
    oldPrimary.setId(UUID.randomUUID());
    ref.deactivate();

    ColorPartnerCode replacement = ref.reactivateWithNewCode(" new-code ", "New name");

    assertThat(replacement.getExternalCode()).isEqualTo("new-code");
    assertThat(replacement.getExternalCodeKey()).isEqualTo("NEW-CODE");
    assertThat(replacement.isPrimary()).isTrue();
    assertThat(oldPrimary.getIsActive()).isFalse();
  }

  @Test
  void toleranceAcceptsNullOrPositiveAndRejectsZeroOrNegative() {
    ColorPartnerRef ref = ref("PRIMARY");

    ref.updateTolerance(new BigDecimal("1.25"));
    assertThat(ref.getDeltaETolerance()).isEqualByComparingTo("1.25");
    ref.updateTolerance(null);
    assertThat(ref.getDeltaETolerance()).isNull();
    assertThatThrownBy(() -> ref.updateTolerance(BigDecimal.ZERO))
        .isInstanceOf(ColorPartnerRefDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(422);
    assertThatThrownBy(() -> ref.updateTolerance(new BigDecimal("-0.01")))
        .isInstanceOf(ColorPartnerRefDomainException.class);
    assertThatThrownBy(() -> ref.updateTolerance(new BigDecimal("100.00")))
        .isInstanceOf(ColorPartnerRefDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(422);
    assertThatThrownBy(() -> ref.updateTolerance(new BigDecimal("1.234")))
        .isInstanceOf(ColorPartnerRefDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(422);
  }

  private ColorPartnerRef ref(String code) {
    ColorPartnerRef ref =
        ColorPartnerRef.create(
            TENANT_ID, COLOR_ID, PARTNER_ID, PartnerRole.CUSTOMER, null, code, null);
    ref.setId(UUID.randomUUID());
    return ref;
  }
}
