package com.fabricmanagement.common.infrastructure.web;

import java.util.List;
import java.util.UUID;

/** Canonical frontend route templates and builders emitted by backend contracts. */
public final class AppRoutes {
  public static final String SALES_ORDER_TEMPLATE = "/sales/{orderId}";
  public static final String ORDER_COVER_DECISION_TEMPLATE = "/decisions/order-cover/{orderId}";

  /** The customer's page for approving a sent order version; public, behind the e-mailed link. */
  public static final String ORDER_APPROVAL_TEMPLATE = "/orders/approve/{token}";

  private AppRoutes() {}

  public static String salesOrder(UUID orderId) {
    return SALES_ORDER_TEMPLATE.replace("{orderId}", orderId.toString());
  }

  public static String orderCoverDecision(UUID orderId) {
    return ORDER_COVER_DECISION_TEMPLATE.replace("{orderId}", orderId.toString());
  }

  public static String orderApproval(String token) {
    return ORDER_APPROVAL_TEMPLATE.replace("{token}", token);
  }

  public static List<Template> templates() {
    return List.of(
        new Template("salesOrder", SALES_ORDER_TEMPLATE),
        new Template("orderCoverDecision", ORDER_COVER_DECISION_TEMPLATE),
        new Template("orderApproval", ORDER_APPROVAL_TEMPLATE));
  }

  public record Template(String name, String template) {}
}
