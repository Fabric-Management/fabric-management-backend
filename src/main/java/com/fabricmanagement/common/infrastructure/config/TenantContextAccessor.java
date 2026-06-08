package com.fabricmanagement.common.infrastructure.config;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext.TenantSnapshot;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Micrometer ThreadLocalAccessor for TenantContext. Registers with ContextRegistry so that
 * ContextPropagatingTaskDecorator handles tenant propagation automatically.
 */
@Component
public class TenantContextAccessor implements ThreadLocalAccessor<TenantSnapshot> {

  public static final String KEY = "tenantContext";

  @PostConstruct
  public void register() {
    ContextRegistry.getInstance().registerThreadLocalAccessor(this);
  }

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public TenantSnapshot getValue() {
    return TenantContext.capture();
  }

  @Override
  public void setValue(TenantSnapshot value) {
    TenantContext.restore(value);
  }

  @Override
  public void setValue() {
    TenantContext.clear();
  }
}
