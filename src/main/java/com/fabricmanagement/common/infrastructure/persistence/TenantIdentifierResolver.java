package com.fabricmanagement.common.infrastructure.persistence;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@link CurrentTenantIdentifierResolver} that supplies the tenant ID from {@link
 * TenantContext} to Hibernate.
 *
 * <p>Implements {@link HibernatePropertiesCustomizer} to explicitly register itself because Spring
 * Boot 3.3.13 / Hibernate 6.4 does not automatically detect both MTCP and Resolver beans
 * simultaneously in some configurations.
 */
@Component
public class TenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

  @Override
  public String resolveCurrentTenantIdentifier() {
    // If context is null, return the SYSTEM_TENANT_ID as a sentinel string.
    // This enforces "deny-by-default" when T3 RLS policies are active:
    // the connection will be bound to SYSTEM_TENANT_ID, which has no data rows,
    // so any read without a valid context will return empty results.
    java.util.UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    return tenantId != null ? tenantId.toString() : TenantContext.SYSTEM_TENANT_ID.toString();
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    // Re-validate the tenant identifier for each existing session
    return true;
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
