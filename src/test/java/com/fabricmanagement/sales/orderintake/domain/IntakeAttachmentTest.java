package com.fabricmanagement.sales.orderintake.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SOI IK-12: stored files are bounded in size and type. */
class IntakeAttachmentTest {

  @Test
  void acceptsImagesPdfAndTextUpToTenMegabytes() {
    IntakeAttachment pdf = store("application/pdf", new byte[] {1, 2, 3});
    assertThat(pdf.getSizeBytes()).isEqualTo(3);
    assertThatThrownBy(() -> store("application/zip", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store("image/png", new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store("image/png", new byte[(int) IntakeAttachment.MAX_BYTES + 1]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aFileBelongsToOneRequest() {
    IntakeAttachment file = store("image/jpeg", new byte[] {1});
    UUID request = UUID.randomUUID();
    file.linkTo(request);
    file.linkTo(request);
    assertThatThrownBy(() -> file.linkTo(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  private static IntakeAttachment store(String type, byte[] content) {
    return IntakeAttachment.store(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        IntakeAttachmentKind.SAMPLE_PHOTO,
        "sample.jpg",
        type,
        content,
        "0".repeat(64),
        UUID.randomUUID(),
        Instant.now());
  }
}
