package com.fabricmanagement.production.core.stockunit.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Sales implements this boundary; production never reads a commercial entity or repository. */
public interface StockUnitCutTargetPort {
  @io.swagger.v3.oas.annotations.media.Schema(name = "StockUnitCutTargetType", enumAsRef = true)
  enum TargetType {
    SALES_ORDER,
    QUOTE,
    DRAFT_LINE
  }

  record Target(TargetType type, UUID documentId, UUID lineId) {}

  record Context(
      Target target,
      UUID productId,
      BigDecimal quantity,
      String fingerprint,
      boolean active,
      boolean editable,
      boolean executionReleased,
      boolean canPrepare,
      boolean canOperate,
      String route,
      String reference,
      Long documentVersion) {}

  Context resolve(Target target, UUID actor, boolean lock);

  boolean sourcesCompatible(Target target, java.util.List<UUID> sourceIds);

  java.util.List<UUID> candidateIds(Target target);

  void assertFreeSources(Target target, java.util.List<UUID> sourceIds, UUID sessionId);

  java.util.Map<UUID, BigDecimal> sourceBudgets(Target target, UUID sessionId);

  /** A same-transaction signal into the existing planning job; no new task runtime. */
  void changed(Context context);
}
