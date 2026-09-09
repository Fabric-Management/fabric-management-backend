package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PermissionKeyTest {
  @Test
  void everyWireValueRoundTripsWithoutPublishingJavaConstantNames() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    for (PermissionKey key : PermissionKey.values()) {
      String json = mapper.writeValueAsString(key);
      assertThat(json).isEqualTo("\"" + key.key() + "\"");
      assertThat(mapper.readValue(json, PermissionKey.class)).isEqualTo(key);
      assertThat(PermissionKey.of(key.resource(), key.action())).contains(key);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "sales,deliver",
    "dashboard,cancel",
    "colors,ship",
    "Sales,read",
    "sales,READ",
    "colours,read"
  })
  void independentValidHalvesDoNotMakeAValidPair(String resource, String action) {
    assertThat(PermissionKey.of(resource, action)).isEmpty();
  }

  @Test
  void malformedAndNullHalvesAreNotNormalised() {
    assertThat(PermissionKey.of(null, "read")).isEmpty();
    assertThat(PermissionKey.of("sales", null)).isEmpty();
    assertThat(PermissionKey.of("", "read")).isEmpty();
    assertThat(PermissionKey.of("sales", " read ")).isEmpty();
    assertThat(PermissionKey.of("sales:read", "")).isEmpty();
  }
}
