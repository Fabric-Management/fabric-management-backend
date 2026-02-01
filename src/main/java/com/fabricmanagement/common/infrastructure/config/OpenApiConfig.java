package com.fabricmanagement.common.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration. Customizes API info and optional server list for documentation.
 */
@Configuration
public class OpenApiConfig {

  @Value("${spring.application.name:fabric-management-system}")
  private String applicationName;

  @Value("${info.app.description:Modular Monolith Fabric Management System}")
  private String description;

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title(applicationName)
                .description(description)
                .version("1.0.0")
                .contact(
                    new Contact()
                        .name("Fabric Management Team")
                        .url("https://github.com/fabric-management")))
        .servers(
            List.of(
                new Server().url("/").description("Current host"),
                new Server().url("http://localhost:8080").description("Local")));
  }
}
