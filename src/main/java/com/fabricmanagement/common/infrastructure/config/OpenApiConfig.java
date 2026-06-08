package com.fabricmanagement.common.infrastructure.config;

import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
        .servers(List.of(new Server().url("/").description("Current host")));
  }

  @Bean
  public OpenApiCustomizer globalErrorResponsesCustomizer() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }

      // Add ApiProblemDetail and its dependencies to components
      ResolvedSchema resolvedSchema =
          ModelConverters.getInstance().readAllAsResolvedSchema(ApiProblemDetail.class);
      openApi.getComponents().addSchemas("ApiProblemDetail", resolvedSchema.schema);
      if (resolvedSchema.referencedSchemas != null) {
        resolvedSchema.referencedSchemas.forEach(
            (k, v) -> openApi.getComponents().addSchemas(k, v));
      }

      // Create a reference schema
      Schema<?> errorSchema = new Schema<>().$ref("#/components/schemas/ApiProblemDetail");

      // Set content formats for error responses
      Content errorContent =
          new Content()
              .addMediaType(
                  org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                  new MediaType().schema(errorSchema))
              .addMediaType(
                  org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                  new MediaType().schema(errorSchema));

      // Inject standard error responses to all operations if missing
      openApi
          .getPaths()
          .values()
          .forEach(
              pathItem -> {
                pathItem
                    .readOperations()
                    .forEach(
                        operation -> {
                          ApiResponses apiResponses = operation.getResponses();

                          addErrorResponse(apiResponses, "400", "Bad Request", errorContent);
                          addErrorResponse(apiResponses, "401", "Unauthorized", errorContent);
                          addErrorResponse(apiResponses, "403", "Forbidden", errorContent);
                          addErrorResponse(apiResponses, "404", "Not Found", errorContent);
                          addErrorResponse(apiResponses, "409", "Conflict", errorContent);
                          addErrorResponse(
                              apiResponses, "422", "Unprocessable Entity", errorContent);
                          addErrorResponse(
                              apiResponses, "500", "Internal Server Error", errorContent);
                        });
              });
    };
  }

  private void addErrorResponse(
      ApiResponses apiResponses, String code, String description, Content content) {
    if (!apiResponses.containsKey(code)) {
      apiResponses.addApiResponse(
          code, new ApiResponse().description(description).content(content));
    }
  }
}
