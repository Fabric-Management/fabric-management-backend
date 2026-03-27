package com.fabricmanagement.common.util;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class PiiMaskingInitializer implements ApplicationListener<ContextRefreshedEvent> {

  @Override
  public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
    ApplicationContext ctx = event.getApplicationContext();
    PiiMaskingUtil.init(ctx);
  }
}
