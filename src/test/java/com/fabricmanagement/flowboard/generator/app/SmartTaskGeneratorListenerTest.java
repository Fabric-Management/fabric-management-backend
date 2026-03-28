package com.fabricmanagement.flowboard.generator.app;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmartTaskGeneratorListener")
class SmartTaskGeneratorListenerTest {

  @Mock private EventRouterService eventRouterService;

  @InjectMocks private SmartTaskGeneratorListener listener;

  @Test
  @DisplayName("SalesOrderConfirmedEvent alındığında EventRouterService'e delege eder")
  void delegatesSalesOrderConfirmedEvent() {
    SalesOrderConfirmedEvent event = mock(SalesOrderConfirmedEvent.class);
    listener.onSalesOrderConfirmed(event);
    verify(eventRouterService).route(event);
  }

  @Test
  @DisplayName("WorkOrderApprovedEvent alındığında EventRouterService'e delege eder")
  void delegatesWorkOrderApprovedEvent() {
    WorkOrderApprovedEvent event = mock(WorkOrderApprovedEvent.class);
    listener.onWorkOrderApproved(event);
    verify(eventRouterService).route(event);
  }

  @Test
  @DisplayName("GoodsReceiptConfirmedEvent alındığında EventRouterService'e delege eder")
  void delegatesGoodsReceiptConfirmedEvent() {
    GoodsReceiptConfirmedEvent event = mock(GoodsReceiptConfirmedEvent.class);
    listener.onGoodsReceiptConfirmed(event);
    verify(eventRouterService).route(event);
  }
}
