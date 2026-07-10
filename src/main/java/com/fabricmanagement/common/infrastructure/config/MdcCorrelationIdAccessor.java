package com.fabricmanagement.common.infrastructure.config;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Propagates the DomainEvent correlation id MDC entry through async task decoration. */
@Component
public class MdcCorrelationIdAccessor implements ThreadLocalAccessor<String> {

  public static final String KEY = "mdc.correlationId";
  private static final String MDC_KEY = "correlationId";

  @PostConstruct
  public void register() {
    ContextRegistry.getInstance().registerThreadLocalAccessor(this);
  }

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public String getValue() {
    return MDC.get(MDC_KEY);
  }

  @Override
  public void setValue(String value) {
    if (value == null) {
      MDC.remove(MDC_KEY);
    } else {
      MDC.put(MDC_KEY, value);
    }
  }

  @Override
  public void setValue() {
    MDC.remove(MDC_KEY);
  }
}
