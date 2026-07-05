package com.fabricmanagement.platform.tradingpartner.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContact;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.dto.CreatePartnerContactRequest;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerContactDto;
import com.fabricmanagement.platform.tradingpartner.infra.repository.PartnerContactRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PartnerContactServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PARTNER_ID = UUID.randomUUID();

  @Mock private PartnerContactRepository contactRepository;
  @Mock private TradingPartnerRepository partnerRepository;

  @InjectMocks private PartnerContactService service;

  private TradingPartner partner;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    partner = partner(PARTNER_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void listContactsScopesByCurrentTenantAndOptionalRole() {
    PartnerContact contact = contact(UUID.randomUUID(), partner, PartnerContactRole.BUYER, true);
    when(partnerRepository.findByTenantIdAndId(TENANT_ID, PARTNER_ID))
        .thenReturn(Optional.of(partner));
    when(contactRepository.findActiveByTenantIdAndPartnerIdAndRole(
            TENANT_ID, PARTNER_ID, PartnerContactRole.BUYER))
        .thenReturn(List.of(contact));

    List<PartnerContactDto> result = service.listContacts(PARTNER_ID, PartnerContactRole.BUYER);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().partnerId()).isEqualTo(PARTNER_ID);
    verify(contactRepository)
        .findActiveByTenantIdAndPartnerIdAndRole(TENANT_ID, PARTNER_ID, PartnerContactRole.BUYER);
  }

  @Test
  void creatingPrimaryContactDemotesExistingPrimaryForSameRole() {
    PartnerContact existingPrimary =
        contact(UUID.randomUUID(), partner, PartnerContactRole.BUYER, true);
    CreatePartnerContactRequest request = new CreatePartnerContactRequest();
    request.setName("New Buyer");
    request.setEmail("new@example.com");
    request.setRole(PartnerContactRole.BUYER);
    request.setPrimary(true);

    when(partnerRepository.findByTenantIdAndId(TENANT_ID, PARTNER_ID))
        .thenReturn(Optional.of(partner));
    when(contactRepository.findActivePrimaryByTenantIdAndPartnerIdAndRole(
            TENANT_ID, PARTNER_ID, PartnerContactRole.BUYER))
        .thenReturn(List.of(existingPrimary));
    when(contactRepository.save(any(PartnerContact.class))).thenAnswer(inv -> inv.getArgument(0));

    service.createContact(PARTNER_ID, request);

    assertThat(existingPrimary.isPrimary()).isFalse();
    verify(contactRepository).saveAll(List.of(existingPrimary));

    ArgumentCaptor<PartnerContact> contactCaptor = ArgumentCaptor.forClass(PartnerContact.class);
    verify(contactRepository).save(contactCaptor.capture());
    PartnerContact saved = contactCaptor.getValue();
    assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(saved.getPartner().getId()).isEqualTo(PARTNER_ID);
    assertThat(saved.isPrimary()).isTrue();
    assertThat(saved.isWhatsappEnabled()).isFalse();
  }

  private TradingPartner partner(UUID partnerId) {
    TradingPartnerRegistry registry =
        TradingPartnerRegistry.builder().id(UUID.randomUUID()).officialName("Acme").build();
    TradingPartner tradingPartner =
        TradingPartner.builder()
            .registry(registry)
            .partnerType(PartnerType.CUSTOMER)
            .customName("Acme")
            .build();
    tradingPartner.setId(partnerId);
    tradingPartner.setTenantId(TENANT_ID);
    return tradingPartner;
  }

  private PartnerContact contact(
      UUID contactId, TradingPartner partner, PartnerContactRole role, boolean primary) {
    PartnerContact contact =
        PartnerContact.builder()
            .partner(partner)
            .name("Buyer")
            .email("buyer@example.com")
            .role(role)
            .primaryContact(primary)
            .build();
    contact.setId(contactId);
    contact.setTenantId(TENANT_ID);
    contact.setIsActive(true);
    return contact;
  }
}
