package com.fabricmanagement.common.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Single application {@link AsyncConfigurer}: tenant propagation for {@code @Async} and global
 * uncaught-exception logging. (Spring allows only one {@code AsyncConfigurer} bean.)
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("async-tenant-");
    executor.setTaskDecorator(new TenantAwareTaskDecorator());
    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new LoggingAsyncUncaughtExceptionHandler();
  }
}
