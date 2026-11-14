package com.fabricmanagement.platform.organization.infra;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether a tax ID belongs to an organisation in any tenant.
 *
 * <p>A request's own connection sees only its tenant's rows under row-level security, and a public
 * signup carries no tenant at all, so a repository query can never answer "does any other tenant
 * already have this company?". It reads one column through the system role instead, and returns
 * only the yes or no (TD-17).
 */
@Component
@RequiredArgsConstructor
public class OrganizationTaxIdLookup {

  private final SystemTransactionExecutor systemExecutor;

  /** True when an organisation in any tenant has exactly this tax ID. */
  public boolean existsInAnyTenant(String taxId) {
    if (taxId == null || taxId.isBlank()) {
      return false;
    }
    Boolean found =
        systemExecutor.executeQueryForObject(
            "SELECT EXISTS (SELECT 1 FROM common_company.common_organization WHERE tax_id = ?)",
            (rs, rowNum) -> rs.getBoolean(1),
            taxId);
    return Boolean.TRUE.equals(found);
  }
}
