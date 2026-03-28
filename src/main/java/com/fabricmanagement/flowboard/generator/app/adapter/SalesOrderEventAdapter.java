package com.fabricmanagement.flowboard.generator.app.adapter;

import com.fabricmanagement.flowboard.generator.app.StockControlEngine;
import com.fabricmanagement.flowboard.generator.app.StockControlEngine.StockDecision;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesOrderEventAdapter implements DomainEventAdapter<SalesOrderConfirmedEvent> {

  private final StockControlEngine stockControlEngine;

  @Override
  public Class<SalesOrderConfirmedEvent> getSupportedEventType() {
    return SalesOrderConfirmedEvent.class;
  }

  @Override
  public String getEventTypeName() {
    return "SalesOrderConfirmed";
  }

  @Override
  public TaskTemplateContext buildContext(SalesOrderConfirmedEvent event) {
    Map<String, String> vars = new HashMap<>();
    vars.put("salesOrder.orderNumber", event.getOrderNumber());
    vars.put(
        "salesOrder.customerName", event.getCustomerName() != null ? event.getCustomerName() : "");

    return new TaskTemplateContext(
        event.getTenantId(),
        event.getSalesOrderId(),
        "SALES_ORDER",
        event.getOrderNumber(),
        event.getRequestedDeliveryDate(),
        vars);
  }

  @Override
  public List<TaskType> determineTaskTypes(
      SalesOrderConfirmedEvent event, List<TaskType> activeTemplateTaskTypes) {
    // SalesOrder için özel kural: StockControlEngine devreye girer
    List<StockDecision> decisions = stockControlEngine.analyze(event);
    return decisions.stream().map(StockDecision::taskType).toList();
  }
}
