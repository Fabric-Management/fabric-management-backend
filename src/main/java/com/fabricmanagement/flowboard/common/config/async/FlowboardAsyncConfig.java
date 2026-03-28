package com.fabricmanagement.flowboard.common.config.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * FlowBoard modülü için Asynchronous execution konfigurasyonu. Global @Async hatalarını
 * FlowboardAsyncExceptionHandler ile yakalar.
 */
@Configuration
@EnableAsync
public class FlowboardAsyncConfig implements AsyncConfigurer {

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new FlowboardAsyncExceptionHandler();
  }
}
