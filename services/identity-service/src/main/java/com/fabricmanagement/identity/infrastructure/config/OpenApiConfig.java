package com.fabricmanagement.identity.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single Responsibility: OpenAPI configuration only
 * Open/Closed: Can be extended without modification
 * Configuration for API documentation
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Identity Service API")
                .version("1.0.0")
                .description("Identity Service for authentication and authorization")
                .contact(new Contact()
                    .name("Fabric Management Team")
                    .email("team@fabricmanagement.com")
                    .url("https://fabricmanagement.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")));
    }
}