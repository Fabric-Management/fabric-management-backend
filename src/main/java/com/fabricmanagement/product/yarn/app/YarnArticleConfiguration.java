package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.product.yarn.domain.article.YarnArticleSpecSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class YarnArticleConfiguration {

  @Bean
  YarnArticleSpecSerializer yarnArticleSpecSerializer(ObjectMapper objectMapper) {
    return new YarnArticleSpecSerializer(objectMapper);
  }
}
