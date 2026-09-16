package com.fabricmanagement.common.infrastructure.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/** Stable content identity: sorted object keys, lossless normalised decimal text. */
public final class CanonicalJsonFingerprint {
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .enable(com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
          .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  private CanonicalJsonFingerprint() {}

  public static String of(Object value) {
    return of(value, java.util.Set.of());
  }

  public static String of(Object value, java.util.Set<String> omittedFields) {
    try {
      String canonical =
          MAPPER.writeValueAsString(normalise(MAPPER.valueToTree(value), omittedFields));
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Cannot fingerprint canonical JSON", ex);
    }
  }

  private static JsonNode normalise(JsonNode node, java.util.Set<String> omittedFields) {
    if (node.isObject()) {
      ObjectNode result = JsonNodeFactory.instance.objectNode();
      TreeSet<String> keys = new TreeSet<>();
      node.fieldNames().forEachRemaining(keys::add);
      keys.stream()
          .filter(key -> !omittedFields.contains(key))
          .forEach(key -> result.set(key, normalise(node.get(key), omittedFields)));
      return result;
    }
    if (node.isArray()) {
      var result = JsonNodeFactory.instance.arrayNode();
      node.forEach(value -> result.add(normalise(value, omittedFields)));
      return result;
    }
    if (node.isNumber()) {
      return JsonNodeFactory.instance.numberNode(node.decimalValue().stripTrailingZeros());
    }
    return node;
  }
}
