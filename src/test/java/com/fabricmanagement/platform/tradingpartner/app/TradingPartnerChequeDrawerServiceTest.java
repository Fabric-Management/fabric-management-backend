package com.fabricmanagement.platform.tradingpartner.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerChequeDrawer;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerChequeDrawerException;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerChequeDrawerAddResultDto.Outcome;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerChequeDrawerRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PAYMENT-PLAN-7 R13, R15: one row per name, never deleted, always within one customer. */
@ExtendWith(MockitoExtension.class)
class TradingPartnerChequeDrawerServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID PARTNER = UUID.randomUUID();

  @Mock TradingPartnerRepository partners;
  @Mock TradingPartnerChequeDrawerRepository drawers;
  @InjectMocks TradingPartnerChequeDrawerService service;

  @BeforeEach
  void tenant() {
    TenantContext.setCurrentTenantId(TENANT);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private void partner(PartnerType type) {
    var partner = TradingPartner.builder().partnerType(type).status(PartnerStatus.ACTIVE).build();
    partner.setId(PARTNER);
    partner.setTenantId(TENANT);
    when(partners.lockByTenantIdAndId(TENANT, PARTNER)).thenReturn(Optional.of(partner));
  }

  private static TradingPartnerChequeDrawer row(String name, boolean active) {
    var drawer = TradingPartnerChequeDrawer.create(PARTNER, name, ChequeDrawerType.INDIVIDUAL);
    drawer.setId(UUID.randomUUID());
    if (!active) drawer.deactivate();
    return drawer;
  }

  @Test
  void aNewNameIsRecorded() {
    partner(PartnerType.CUSTOMER);
    when(drawers.findByTenantIdAndPartnerIdAndNormalizedName(TENANT, PARTNER, "ava pryce"))
        .thenReturn(Optional.empty());
    when(drawers.save(any())).thenAnswer(call -> call.getArgument(0));

    var result = service.add(PARTNER, " Ava  Pryce ", ChequeDrawerType.INDIVIDUAL);

    assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
    assertThat(result.drawer().name()).isEqualTo("Ava Pryce");
  }

  @Test
  void anActiveDuplicateIsReturnedForUse() {
    partner(PartnerType.BOTH);
    var existing = row("Ava Pryce", true);
    when(drawers.findByTenantIdAndPartnerIdAndNormalizedName(TENANT, PARTNER, "ava pryce"))
        .thenReturn(Optional.of(existing));

    var result = service.add(PARTNER, "AVA PRYCE", ChequeDrawerType.INDIVIDUAL);

    assertThat(result.outcome()).isEqualTo(Outcome.EXISTING);
    assertThat(result.drawer().id()).isEqualTo(existing.getId());
    verify(drawers, never()).save(any());
  }

  @Test
  void anInactiveDuplicateIsReportedAndNotReactivated() {
    partner(PartnerType.CUSTOMER);
    var existing = row("Ava Pryce", false);
    when(drawers.findByTenantIdAndPartnerIdAndNormalizedName(TENANT, PARTNER, "ava pryce"))
        .thenReturn(Optional.of(existing));

    var result = service.add(PARTNER, "ava pryce", ChequeDrawerType.INDIVIDUAL);

    assertThat(result.outcome()).isEqualTo(Outcome.INACTIVE_DUPLICATE);
    assertThat(result.drawer().active()).isFalse();
    assertThat(existing.isUsable()).isFalse();
    verify(drawers, never()).save(any());
  }

  @Test
  void reactivationReturnsTheSameRowWithinTheCustomer() {
    partner(PartnerType.CUSTOMER);
    var existing = row("Ava Pryce", false);
    when(drawers.findByTenantIdAndPartnerIdAndId(TENANT, PARTNER, existing.getId()))
        .thenReturn(Optional.of(existing));
    when(drawers.save(existing)).thenReturn(existing);

    var result = service.reactivate(PARTNER, existing.getId());

    assertThat(result.id()).isEqualTo(existing.getId());
    assertThat(result.active()).isTrue();
  }

  @Test
  void aDrawerOfAnotherCustomerIsNotFound() {
    partner(PartnerType.CUSTOMER);
    UUID foreign = UUID.randomUUID();
    when(drawers.findByTenantIdAndPartnerIdAndId(TENANT, PARTNER, foreign))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reactivate(PARTNER, foreign))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void renamingOntoAnotherDrawersNameIsRefused() {
    partner(PartnerType.CUSTOMER);
    var drawer = row("Ava Pryce", true);
    var other = row("Oliver Hartley", false);
    when(drawers.findByTenantIdAndPartnerIdAndId(TENANT, PARTNER, drawer.getId()))
        .thenReturn(Optional.of(drawer));
    when(drawers.findByTenantIdAndPartnerIdAndNormalizedName(TENANT, PARTNER, "oliver hartley"))
        .thenReturn(Optional.of(other));

    assertThatThrownBy(
            () ->
                service.change(
                    PARTNER, drawer.getId(), "Oliver Hartley", ChequeDrawerType.INDIVIDUAL))
        .isInstanceOfSatisfying(
            TradingPartnerChequeDrawerException.class,
            refused -> assertThat(refused.getErrorCode()).isEqualTo("CHEQUE_DRAWER_NAME_TAKEN"));
  }

  @Test
  void aSupplierOnlyPartnerGetsNoDrawers() {
    partner(PartnerType.SUPPLIER);

    assertThatThrownBy(() -> service.add(PARTNER, "Ava Pryce", ChequeDrawerType.INDIVIDUAL))
        .isInstanceOfSatisfying(
            TradingPartnerChequeDrawerException.class,
            refused ->
                assertThat(refused.getErrorCode()).isEqualTo("CHEQUE_DRAWER_PARTNER_NOT_CUSTOMER"));
  }

  @Test
  void aRemovedCustomerGetsNoNewDrawers() {
    var partner =
        TradingPartner.builder()
            .partnerType(PartnerType.CUSTOMER)
            .status(PartnerStatus.ACTIVE)
            .build();
    partner.setId(PARTNER);
    partner.setTenantId(TENANT);
    partner.delete();
    when(partners.lockByTenantIdAndId(TENANT, PARTNER)).thenReturn(Optional.of(partner));

    assertThatThrownBy(() -> service.add(PARTNER, "Ava Pryce", ChequeDrawerType.INDIVIDUAL))
        .isInstanceOfSatisfying(
            TradingPartnerChequeDrawerException.class,
            refused ->
                assertThat(refused.getErrorCode()).isEqualTo("CHEQUE_DRAWER_PARTNER_REMOVED"));
  }

  @Test
  void aBlankNameIsRefusedWithItsOwnCode() {
    partner(PartnerType.CUSTOMER);

    assertThatThrownBy(() -> service.add(PARTNER, "   ", ChequeDrawerType.INDIVIDUAL))
        .isInstanceOfSatisfying(
            TradingPartnerChequeDrawerException.class,
            refused -> assertThat(refused.getErrorCode()).isEqualTo("CHEQUE_DRAWER_NAME_INVALID"));
  }
}
