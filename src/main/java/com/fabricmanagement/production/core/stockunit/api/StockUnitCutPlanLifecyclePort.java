package com.fabricmanagement.production.core.stockunit.api;

import java.util.Map;
import java.util.UUID;

/** Synchronous, lightweight lifecycle boundary; it does not depend on cut command orchestration. */
public interface StockUnitCutPlanLifecyclePort {
  void commercialLineChanged(UUID lineId);

  void commercialDocumentChanged(String type, UUID documentId);

  void reconcileDocument(String type, UUID documentId, Map<UUID, String> currentFingerprints);
}
