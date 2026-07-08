package com.fabricmanagement.sales.quote.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public final class QuoteLineLotSnapshotCodec {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<QuoteLineLotSnapshot>> SNAPSHOT_LIST =
      new TypeReference<>() {};

  private QuoteLineLotSnapshotCodec() {}

  public static String toJson(List<QuoteLineLotSnapshot> snapshots) {
    try {
      return OBJECT_MAPPER.writeValueAsString(snapshots == null ? List.of() : snapshots);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize quote line lot snapshot", ex);
    }
  }

  public static List<QuoteLineLotSnapshot> fromJson(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, SNAPSHOT_LIST);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not deserialize quote line lot snapshot", ex);
    }
  }
}
