package com.fabricmanagement.common.infrastructure.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

@Configuration
@Profile("test")
public class SchedulingDisabledConfig {

  @Bean
  static BeanDefinitionRegistryPostProcessor removeScheduledAnnotationProcessor() {
    return new BeanDefinitionRegistryPostProcessor() {
      @Override
      public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
          throws BeansException {
        String beanName = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;
        if (registry.containsBeanDefinition(beanName)) {
          registry.removeBeanDefinition(beanName);
        }
      }

      @Override
      public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
          throws BeansException {
        // No-op.
      }
    };
  }
}
