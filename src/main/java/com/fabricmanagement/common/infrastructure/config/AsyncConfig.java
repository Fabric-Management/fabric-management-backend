package com.fabricmanagement.common.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async configuration that ensures {@link
 * com.fabricmanagement.common.infrastructure.persistence.TenantContext} is properly propagated to
 * {@code @Async} worker threads via {@link TenantAwareTaskDecorator}.
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
}
