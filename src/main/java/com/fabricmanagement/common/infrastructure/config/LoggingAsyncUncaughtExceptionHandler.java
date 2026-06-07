package com.fabricmanagement.common.infrastructure.config;

import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

/**
 * Logs uncaught exceptions from {@code @Async} methods (e.g. transactional event listeners). Used
 * as the application-wide {@link AsyncConfigurer#getAsyncUncaughtExceptionHandler()} delegate.
 */
@Slf4j
public class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

  @Override
  public void handleUncaughtException(Throwable ex, Method method, Object... params) {
    log.error("==========================================");
    log.error("ASYNC UNCAUGHT EXCEPTION in method: {}", method.getName(), ex);
    for (int i = 0; i < params.length; i++) {
      log.error("Parameter[{}] : {}", i, params[i]);
    }
    log.error("==========================================");
  }
}
