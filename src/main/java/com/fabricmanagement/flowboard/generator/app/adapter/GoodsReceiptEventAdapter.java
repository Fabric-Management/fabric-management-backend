package com.fabricmanagement.flowboard.generator.app.adapter;

import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GoodsReceiptEventAdapter implements DomainEventAdapter<GoodsReceiptConfirmedEvent> {

  @Override
  public Class<GoodsReceiptConfirmedEvent> getSupportedEventType() {
    return GoodsReceiptConfirmedEvent.class;
  }

  @Override
  public String getEventTypeName() {
    return "GoodsReceiptConfirmed";
  }

  @Override
  public TaskTemplateContext buildContext(GoodsReceiptConfirmedEvent event) {
    return new TaskTemplateContext(
        event.tenantId(),
        event.receiptId(),
        "GOODS_RECEIPT",
        event.receiptNumber(),
        null, // Deadline GoodsReceiptConfirmedEvent içerisinde yok
        Map.of()); // Interpolation değişkenleri şimdilik boş
  }
}
