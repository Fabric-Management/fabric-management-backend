package com.fabricmanagement.sales.salesorder.infra;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCoverDetailLinesIT extends OrderCoverIntegrationSupport {
  @Test
  void noEvidenceAndCurrentEvidenceExposeFinalLineDecisions() throws Exception {
    Cover cover = governed(1);
    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].lineId").value(cover.lineIds().getFirst().toString()))
        .andExpect(jsonPath("$.data.lines[0].selectable").value(false))
        .andExpect(jsonPath("$.data.lines[0].blockReason.code").value("NO_EVIDENCE"))
        .andExpect(jsonPath("$.data.lines[0].evidenceId").value(org.hamcrest.Matchers.nullValue()));

    var snapshot = refresh(cover);
    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.subject.accessibleHref").value("/sales/" + cover.orderId()))
        .andExpect(jsonPath("$.data.lines[0].evidenceId").value(snapshot.id().toString()))
        .andExpect(jsonPath("$.data.lines[0].selectable").value(true))
        .andExpect(jsonPath("$.data.lines[0].productionQuantity.value").value("10"));
  }

  @Test
  void lineBlockOutranksTheCaseEvidenceUnknownReason() throws Exception {
    Cover cover = governed(1);
    tx(
        () -> {
          var line = lines.findById(cover.lineIds().getFirst()).orElseThrow();
          // A product is needed for the evidence to report the requirement gap itself; without
          // one the evidence reason is PRODUCT_REQUIREMENT_MISSING and the case stays actionable.
          line.setProductId(
              products
                  .saveAndFlush(
                      com.fabricmanagement.product.core.domain.Product.create(
                          com.fabricmanagement.product.core.domain.ProductType.FABRIC, "kg"))
                  .getId());
          attachProfile(line, line.getRequirementProfileId(), 2, false);
          return null;
        });
    refresh(cover);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions[0].reason.code").value("EVIDENCE_UNKNOWN"))
        .andExpect(jsonPath("$.data.lines[0].blockReason.code").value("REQUIREMENT_INCOMPLETE"))
        .andExpect(
            jsonPath("$.data.lines[0].blockReason.incompleteReasons[0]")
                .value("UNSPECIFIED:WIDTH"));
  }

  @Test
  void caseDenialIsShownOnlyWhenTheLineItselfIsSelectable() throws Exception {
    Cover cover = governed(2);
    refresh(cover);
    var viewer =
        user("Outside pool", "sales:read", "sales:write", "flowboard:read", "flowboard:write");
    authenticate(viewer);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].selectable").value(false))
        .andExpect(jsonPath("$.data.lines[0].blockReason.code").value("ACTION_NOT_ALLOWED"))
        .andExpect(
            jsonPath("$.data.lines[0].blockReason.caseReasonCode").value("OUTSIDE_ROUTING_POOL"))
        .andExpect(jsonPath("$.data.lines[1].selectable").value(false))
        .andExpect(jsonPath("$.data.lines[1].blockReason.code").value("ACTION_NOT_ALLOWED"));
  }

  @Test
  void readablePoolMemberWithoutWritePermissionSeesPermissionDeniedOnEveryLine() throws Exception {
    Cover cover = governed(2);
    refresh(cover);
    var viewer =
        user(
            "Pool member losing write",
            "sales:read",
            "sales:write",
            "flowboard:read",
            "flowboard:write");
    configurePool(viewer.getId());
    revoke(viewer, "sales:write");
    authenticate(viewer);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].blockReason.code").value("ACTION_NOT_ALLOWED"))
        .andExpect(
            jsonPath("$.data.lines[0].blockReason.caseReasonCode").value("PERMISSION_DENIED"))
        .andExpect(jsonPath("$.data.lines[1].blockReason.code").value("ACTION_NOT_ALLOWED"))
        .andExpect(
            jsonPath("$.data.lines[1].blockReason.caseReasonCode").value("PERMISSION_DENIED"));
  }

  @Test
  void identicalCreationTimesUseIdAsTheOrderDetailAndDecisionTieBreaker() throws Exception {
    Cover cover = governed(2);
    Timestamp same = Timestamp.from(Instant.parse("2026-09-22T10:00:00Z"));
    jdbc.update(
        "update sales_ord.sales_order_line set created_at=? where sales_order_id=?",
        same,
        cover.orderId());
    refresh(cover);
    var expected =
        jdbc.queryForList(
            "select id from sales_ord.sales_order_line where sales_order_id=? order by created_at,id",
            UUID.class,
            cover.orderId());

    performAuthenticated(get("/api/v1/sales/orders/{orderId}", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].id").value(expected.getFirst().toString()))
        .andExpect(jsonPath("$.data.lines[1].id").value(expected.getLast().toString()));
    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].lineId").value(expected.getFirst().toString()))
        .andExpect(jsonPath("$.data.lines[0].lineNumber").value(1))
        .andExpect(jsonPath("$.data.lines[1].lineId").value(expected.getLast().toString()))
        .andExpect(jsonPath("$.data.lines[1].lineNumber").value(2));
    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].lineId").value(expected.getFirst().toString()))
        .andExpect(jsonPath("$.data.lines[1].lineId").value(expected.getLast().toString()));
  }

  @Test
  void scopeLineWithoutAnActiveOrderLineIsOmittedInsteadOfFailingTheRead() throws Exception {
    Cover cover = governed(2);
    refresh(cover);
    UUID deactivated = cover.lineIds().getLast();
    jdbc.update("update sales_ord.sales_order_line set is_active=false where id=?", deactivated);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines.length()").value(1))
        .andExpect(
            jsonPath("$.data.lines[0].lineId")
                .value(org.hamcrest.Matchers.not(deactivated.toString())))
        .andExpect(jsonPath("$.data.lines[0].lineNumber").value(1));
  }
}
