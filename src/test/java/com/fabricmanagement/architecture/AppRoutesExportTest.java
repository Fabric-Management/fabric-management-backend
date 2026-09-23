package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.web.AppRoutes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppRoutesExportTest {
  @Test
  void buildersUseTheExportedFrontendTemplates() {
    UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000123");
    assertThat(AppRoutes.salesOrder(orderId))
        .isEqualTo("/sales/00000000-0000-0000-0000-000000000123");
    assertThat(AppRoutes.orderCoverDecision(orderId))
        .isEqualTo("/decisions/order-cover/00000000-0000-0000-0000-000000000123");
  }

  @Test
  void exportedFrontendRoutesMatchTheCanonicalBuilders() throws Exception {
    var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    String generated = mapper.writeValueAsString(AppRoutes.templates()) + System.lineSeparator();
    Path target = Path.of("api/app-routes.json");
    boolean update =
        Boolean.parseBoolean(System.getProperty("UPDATE_OPENAPI", "false"))
            || Boolean.parseBoolean(System.getenv("UPDATE_OPENAPI"));
    if (!Files.exists(target) || update) {
      Files.createDirectories(target.getParent());
      Files.writeString(target, generated);
    } else {
      assertThat(Files.readString(target))
          .as("Frontend app routes drifted; regenerate with UPDATE_OPENAPI=true")
          .isEqualTo(generated);
    }
  }
}
