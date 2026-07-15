package com.fabricmanagement.costing.domain.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostTemplateItemTest {

  @Test
  void supportsJavaSerializationForJsonTypeDeepCopies() throws Exception {
    var item = new CostTemplateItem("LABOR", new BigDecimal("0.12"), true);

    byte[] serialized;
    try (var bytes = new ByteArrayOutputStream();
        var output = new ObjectOutputStream(bytes)) {
      output.writeObject(item);
      serialized = bytes.toByteArray();
    }

    CostTemplateItem deserialized;
    try (var input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      deserialized = (CostTemplateItem) input.readObject();
    }

    assertThat(deserialized).isEqualTo(item);
  }
}
