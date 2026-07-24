package com.fabricmanagement.platform.tradingpartner.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreateCustomerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.tradingpartner.dto.UpdateTradingPartnerRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradingPartnerControllerTest {

  @Mock private TradingPartnerService tradingPartnerService;

  private TradingPartnerController controller;
  private UUID actingUserId;

  @BeforeEach
  void setUp() {
    controller = new TradingPartnerController(tradingPartnerService);
    actingUserId = UUID.randomUUID();
    TenantContext.setCurrentUserId(actingUserId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createPartnerPassesActingUserAsInternalAcquirer() {
    CreateTradingPartnerRequest request =
        CreateTradingPartnerRequest.builder()
            .companyName("Customer Ltd")
            .partnerType(PartnerType.CUSTOMER)
            .build();
    when(tradingPartnerService.createPartner(request, actingUserId))
        .thenReturn(TradingPartnerDto.builder().id(UUID.randomUUID()).build());

    controller.createPartner(request);

    verify(tradingPartnerService).createPartner(request, actingUserId);
  }

  @Test
  void quickCreatePassesActingUserAsInternalAcquirer() {
    QuickCreateCustomerRequest request = new QuickCreateCustomerRequest();
    when(tradingPartnerService.quickCreateCustomer(request, actingUserId))
        .thenReturn(TradingPartnerDto.builder().id(UUID.randomUUID()).build());

    controller.quickCreateCustomer(request);

    verify(tradingPartnerService).quickCreateCustomer(request, actingUserId);
  }

  @Test
  void updatePartnerPassesActingUserAsInternalAcquirer() {
    UUID partnerId = UUID.randomUUID();
    UpdateTradingPartnerRequest request =
        UpdateTradingPartnerRequest.builder().partnerType(PartnerType.CUSTOMER).build();
    when(tradingPartnerService.updatePartner(partnerId, request, actingUserId))
        .thenReturn(TradingPartnerDto.builder().id(partnerId).build());

    controller.updatePartner(partnerId, request);

    verify(tradingPartnerService).updatePartner(partnerId, request, actingUserId);
  }
}
