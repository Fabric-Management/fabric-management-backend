package com.fabricmanagement.flowboard.generator.domain.catalogue;

import java.util.List;

/** Per-tenant totals of one reconciliation run. */
public record CatalogueReconciliationResult(
    int inserted, int adopted, int unchanged, List<CatalogueFinding> findings) {

  public CatalogueReconciliationResult {
    findings = List.copyOf(findings);
  }
}
