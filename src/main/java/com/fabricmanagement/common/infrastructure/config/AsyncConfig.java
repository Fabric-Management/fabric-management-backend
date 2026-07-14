package com.fabricmanagement.common.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Single application {@link AsyncConfigurer}: tenant propagation for {@code @Async} and global
 * uncaught-exception logging. (Spring allows only one {@code AsyncConfigurer} bean.)
 *
 * <p>The async bean post-processor must run after infrastructure auto-proxy creators so it can add
 * its advisor to proxies that already carry transaction and Spring Modulith publication-completion
 * advice. {@code AsyncAnnotationBeanPostProcessor} inserts its advisor before existing advisors, so
 * asynchronous execution remains the outer boundary and tenant restoration still runs on the
 * executor thread before the transaction starts.
 */
@Configuration
@EnableAsync(proxyTargetClass = true, order = Ordered.LOWEST_PRECEDENCE)
public class AsyncConfig implements AsyncConfigurer {

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("async-tenant-");
    executor.setTaskDecorator(
        new org.springframework.core.task.support.ContextPropagatingTaskDecorator());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(15);
    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new LoggingAsyncUncaughtExceptionHandler();
  }
}
