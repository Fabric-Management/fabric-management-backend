package com.fabricmanagement.platform.tenant.domain.port;

import java.util.List;

/** Outcome of provisioning one tenant; {@code findings} need an operator but are not failures. */
public record CatalogueProvisioningReport(
    int inserted, int adopted, int unchanged, List<String> findings) {

  public CatalogueProvisioningReport {
    findings = List.copyOf(findings);
  }
}
