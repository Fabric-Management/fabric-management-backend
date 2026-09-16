package com.fabricmanagement.sales.salesorder.dto;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.core.converter.ModelConverters;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCoverEvidenceContractTest {
  @Test
  void schemaDictionaryPinsNamesPropertiesAndRequiredNullableFields() throws Exception {
    com.fasterxml.jackson.databind.JsonNode dictionary;
    try (var resource = getClass().getResourceAsStream("/contracts/order-cover-dictionary.json")) {
      assertThat(resource)
          .as("Generated schema dictionary must be committed with the backend")
          .isNotNull();
      dictionary = new ObjectMapper().readTree(resource);
    }
    assertThat(dictionary.path("formatVersion").asInt()).isEqualTo(1);
    var schemas = ModelConverters.getInstance().readAll(OrderCoverEvidenceDto.class);
    for (String name :
        List.of(
            "OrderCoverEvidence",
            "OrderCoverLineEvidence",
            "OrderCoverCompetingAllocation",
            "OrderCoverQuantity",
            "OrderCoverSource")) {
      var row = dictionary.path("schemas").path(name);
      assertThat(row.isTextual()).as("Missing schema %s", name).isTrue();
      String fields = row.asText();
      var names =
          Arrays.stream(fields.split(";"))
              .map(field -> field.split(":", 2)[0].trim().replace("?", "").replace("[]", ""))
              .toList();
      io.swagger.v3.oas.models.media.Schema<?> schema = schemas.get(name);
      assertThat(schema).as(name).isNotNull();
      assertThat(schema.getRequired())
          .as(name + " required fields")
          .containsExactlyInAnyOrderElementsOf(names);
      assertThat(schema.getProperties().keySet())
          .as(name + " properties")
          .containsExactlyInAnyOrderElementsOf(names);
      // '?' means required-but-nullable in this dictionary, not an omitted JSON property.
      Arrays.stream(fields.split(";"))
          .filter(field -> field.split(":", 2)[0].contains("?"))
          .map(field -> field.split(":", 2)[0].trim().replace("?", ""))
          .forEach(field -> assertThat(schema.getProperties().get(field).getNullable()).isTrue());
      if (name.equals("OrderCoverLineEvidence")) {
        String suitability =
            Arrays.stream(fields.split(";"))
                .map(String::trim)
                .filter(field -> field.startsWith("suitability:"))
                .findFirst()
                .orElseThrow();
        assertThat(Arrays.stream(Suitability.values()).map(Enum::name).toList())
            .containsExactlyInAnyOrder(suitability.split(":", 2)[1].trim().split("/"));
      }
    }
    io.swagger.v3.oas.models.media.Schema<?> quantitySchema = schemas.get("OrderCoverQuantity");
    assertThat(quantitySchema.getProperties().get("value").getType()).isEqualTo("string");
    assertThat(quantitySchema.getProperties().get("value").getNullable()).isTrue();
  }

  @Test
  void largeDecimalsAreLosslessStringsAndUnknownIsExplicitNull() throws Exception {
    var mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    var known = Quantity.known(new BigDecimal("12345678901234567890.123456789"), "KG");
    var unknown = Quantity.unknown(null, "PRIMARY_MEASURE_UNKNOWN");
    var row =
        new Line(
            UUID.randomUUID(),
            0,
            null,
            known,
            unknown,
            unknown,
            unknown,
            Suitability.UNKNOWN,
            List.of(),
            List.of("CHECK_MATERIAL_AVAILABILITY"),
            List.of(),
            List.of("PRIMARY_MEASURE_UNKNOWN"));
    var dto =
        new OrderCoverEvidenceDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            0,
            Instant.parse("2026-09-15T12:00:00Z"),
            "a".repeat(64),
            "ORDER_COVER_EVIDENCE_V1",
            List.of(row));
    var json = mapper.readTree(mapper.writeValueAsBytes(dto));
    assertThat(json.at("/lines/0/requested/value").isTextual()).isTrue();
    assertThat(json.at("/lines/0/requested/value").asText()).isEqualTo(known.value());
    assertThat(json.at("/lines/0/suitableFree").has("value")).isTrue();
    assertThat(json.at("/lines/0/suitableFree/value").isNull()).isTrue();
    assertThat(json.at("/lines/0/suitableFree").has("unit")).isTrue();
    assertThat(json.at("/lines/0").has("productId")).isTrue();
    assertThat(mapper.readValue(mapper.writeValueAsBytes(dto), OrderCoverEvidenceDto.class))
        .isEqualTo(dto);
  }

  @Test
  void invalidKnowledgeStatesCannotBeSerializedAsFalseCertainty() {
    assertThatThrownBy(() -> new Quantity(Knowledge.UNKNOWN, "0", "KG", "MISSING"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Quantity(Knowledge.UNKNOWN, null, "KG", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Quantity(Knowledge.KNOWN, "1e3", "KG", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Quantity(Knowledge.KNOWN, "100", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
