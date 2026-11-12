package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * JobRunr's tables are created by Flyway with JobRunr's own migrations marked as installed, so
 * every row those migrations would have inserted must come from Flyway too. Without the counter row
 * JobRunr stops its background job server about 75 seconds after start (OBS-DOCKER-ENV-1).
 */
@DisplayName("JobRunr storage seeded by Flyway")
class JobRunrStorageSeedIT extends AbstractIntegrationTest {

  @Autowired private SystemTransactionExecutor systemExecutor;

  @Test
  @DisplayName("the succeeded-jobs counter JobRunr's v009 would insert exists and is a number")
  void succeededJobsCounterExists() {
    List<String> values =
        systemExecutor.executeQuery(
            "SELECT value FROM public.jobrunr_metadata"
                + " WHERE id = 'succeeded-jobs-counter-cluster'"
                + " AND name = 'succeeded-jobs-counter' AND owner = 'cluster'",
            (rs, rowNum) -> rs.getString("value"));

    assertThat(values).singleElement().satisfies(value -> assertThat(value.trim()).isNotBlank());
    assertThat(Long.parseLong(values.getFirst().trim())).isNotNegative();
  }
}
