package com.fabricmanagement.production.masterdata.color.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.color.app.port.TradingPartnerQueryPort;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerCode;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerRef;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorPartnerRefDomainException;
import com.fabricmanagement.production.masterdata.color.dto.AddColorPartnerCodeRequest;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerCodeInput;
import com.fabricmanagement.production.masterdata.color.dto.CreateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.dto.ReactivateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.dto.UpdateColorPartnerRefRequest;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorPartnerRefRepository;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ColorPartnerRefServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID COLOR_ID = UUID.randomUUID();
  private static final UUID PARTNER_ID = UUID.randomUUID();
  private static final UUID REF_ID = UUID.randomUUID();

  @Mock private ColorPartnerRefRepository refRepository;
  @Mock private ColorRepository colorRepository;
  @Mock private TradingPartnerQueryPort partnerQueryPort;
  @Mock private EntityManager entityManager;

  private ColorPartnerRefService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new ColorPartnerRefService(refRepository, colorRepository, partnerQueryPort, entityManager);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void creationRequiresAnActiveColorAndCompatiblePartnerAndPersistsOnePrimary() {
    when(colorRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, COLOR_ID))
        .thenReturn(Optional.of(color()));
    when(partnerQueryPort.isActiveAndCompatible(TENANT_ID, PARTNER_ID, PartnerRole.CUSTOMER))
        .thenReturn(true);
    when(refRepository.save(any(ColorPartnerRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ColorPartnerRef created = service.create(COLOR_ID, createRequest());

    assertThat(created.getCodes()).singleElement().matches(ColorPartnerCode::isPrimary);
    verify(refRepository).save(created);
  }

  @Test
  void missingInactiveOrIncompatiblePartnerBlocksCreationAndOtherMutations() {
    when(colorRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, COLOR_ID))
        .thenReturn(Optional.of(color()));
    when(partnerQueryPort.isActiveAndCompatible(TENANT_ID, PARTNER_ID, PartnerRole.CUSTOMER))
        .thenReturn(false);

    assertThatThrownBy(() -> service.create(COLOR_ID, createRequest()))
        .isInstanceOf(ColorPartnerRefDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(409);

    ColorPartnerRef ref = ref();
    when(refRepository.findForMutationByTenantIdAndId(TENANT_ID, REF_ID))
        .thenReturn(Optional.of(ref));
    assertThatThrownBy(
            () ->
                service.update(COLOR_ID, REF_ID, new UpdateColorPartnerRefRequest(BigDecimal.ONE)))
        .isInstanceOf(ColorPartnerRefDomainException.class);
    assertThatThrownBy(
            () -> service.addCode(COLOR_ID, REF_ID, new AddColorPartnerCodeRequest("ALIAS", null)))
        .isInstanceOf(ColorPartnerRefDomainException.class);
  }

  @Test
  void addingCodeFlushesTheManagedAggregateBeforeReturningTheChild() {
    ColorPartnerRef ref = ref();
    when(refRepository.findForMutationByTenantIdAndId(TENANT_ID, REF_ID))
        .thenReturn(Optional.of(ref));
    when(colorRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, COLOR_ID))
        .thenReturn(Optional.of(color()));
    when(partnerQueryPort.isActiveAndCompatible(TENANT_ID, PARTNER_ID, PartnerRole.CUSTOMER))
        .thenReturn(true);

    ColorPartnerCode added =
        service.addCode(COLOR_ID, REF_ID, new AddColorPartnerCodeRequest("ALIAS", null));

    assertThat(added.getExternalCode()).isEqualTo("ALIAS");
    verify(entityManager).flush();
    verify(refRepository, never()).save(ref);
  }

  @Test
  void deactivationCleanupDoesNotRequireAnActiveColorOrPartner() {
    ColorPartnerRef ref = ref();
    when(refRepository.findForMutationByTenantIdAndId(TENANT_ID, REF_ID))
        .thenReturn(Optional.of(ref));
    when(refRepository.save(ref)).thenReturn(ref);

    ColorPartnerRef deactivated = service.deactivate(COLOR_ID, REF_ID);

    assertThat(deactivated.getIsActive()).isFalse();
    verify(colorRepository, never()).findByTenantIdAndIdAndIsActiveTrue(any(), any());
    verify(partnerQueryPort, never()).isActiveAndCompatible(any(), any(), any());
  }

  @Test
  void reactivationRequiresPartnerValidationAndExactlyOneAvailableCode() {
    ColorPartnerRef ref = ref();
    ColorPartnerCode primary = ref.primaryCode();
    ref.deactivate();
    when(refRepository.findForMutationByTenantIdAndId(TENANT_ID, REF_ID))
        .thenReturn(Optional.of(ref));
    when(colorRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, COLOR_ID))
        .thenReturn(Optional.of(color()));
    when(partnerQueryPort.isActiveAndCompatible(TENANT_ID, PARTNER_ID, PartnerRole.CUSTOMER))
        .thenReturn(true);
    when(refRepository.save(ref)).thenReturn(ref);

    ColorPartnerRef reactivated =
        service.reactivate(
            COLOR_ID, REF_ID, new ReactivateColorPartnerRefRequest(primary.getId(), null));

    assertThat(reactivated.getIsActive()).isTrue();
    assertThat(reactivated.primaryCode()).isSameAs(primary);
  }

  @Test
  void primarySwitchFlushesBetweenDemotionAndPromotion() {
    ColorPartnerRef ref = ref();
    ColorPartnerCode alias = ref.addCode("ALIAS", null);
    alias.setId(UUID.randomUUID());
    when(refRepository.findForMutationByTenantIdAndId(TENANT_ID, REF_ID))
        .thenReturn(Optional.of(ref));
    when(colorRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT_ID, COLOR_ID))
        .thenReturn(Optional.of(color()));
    when(partnerQueryPort.isActiveAndCompatible(TENANT_ID, PARTNER_ID, PartnerRole.CUSTOMER))
        .thenReturn(true);
    when(refRepository.save(ref)).thenReturn(ref);

    service.makePrimary(COLOR_ID, REF_ID, alias.getId());

    verify(entityManager).flush();
    assertThat(ref.primaryCode()).isSameAs(alias);
  }

  private CreateColorPartnerRefRequest createRequest() {
    return new CreateColorPartnerRefRequest(
        PARTNER_ID, PartnerRole.CUSTOMER, null, new ColorPartnerCodeInput("SS26-NVY-07", null));
  }

  private Color color() {
    Color color = Color.create(TENANT_ID, "NAVY-001", "Navy", "#1F2A44");
    color.setId(COLOR_ID);
    return color;
  }

  private ColorPartnerRef ref() {
    ColorPartnerRef ref =
        ColorPartnerRef.create(
            TENANT_ID, COLOR_ID, PARTNER_ID, PartnerRole.CUSTOMER, null, "PRIMARY", null);
    ref.setId(REF_ID);
    ref.primaryCode().setId(UUID.randomUUID());
    return ref;
  }
}
