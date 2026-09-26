package com.fabricmanagement.flowboard.generator.app.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** T-TXGUARD (playground half): no write outside the clone's system transaction. */
class PlaygroundCatalogueWriterTest {

  @Test
  void refusesToRunWithoutTheSystemTransaction() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PlaygroundCatalogueWriter writer = new PlaygroundCatalogueWriter(jdbc, mock(DataSource.class));

    assertThatThrownBy(
            () -> writer.copyFromSource(UUID.randomUUID(), UUID.randomUUID(), "PG-12345678"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("system transaction");
    verifyNoInteractions(jdbc);
  }

  @Test
  void freshUidUsesTheBaseEntityFormat() {
    assertThat(PlaygroundCatalogueWriter.freshUid("PG-1a2b3c4d"))
        .matches("PG-1a2b3c4d-TMPL-[0-9A-F]{8}");
    assertThat(PlaygroundCatalogueWriter.freshUid(null)).startsWith("SYS-000-TMPL-");
  }
}
