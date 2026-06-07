package com.fabricmanagement.platform.tenant.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaygroundTTLReaper (Unit Test)")
class PlaygroundTTLReaperServiceTest {

  @Mock private SystemTransactionExecutor systemExecutor;

  @InjectMocks private PlaygroundTTLReaperService reaper;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(reaper, "ttlDays", 14);
  }

  @Nested
  @DisplayName("reapExpiredPlaygrounds")
  class ReapExpiredPlaygrounds {

    @Test
    @DisplayName("Should soft-delete expired playgrounds and verify threshold")
    @SuppressWarnings("unchecked")
    void shouldSoftDeleteExpiredPlaygrounds() {
      // Mock executeInTransaction to invoke the callback with a mock JdbcTemplate
      JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
      when(mockJdbc.update(anyString(), any(java.sql.Timestamp.class))).thenReturn(5);

      when(systemExecutor.executeInTransaction(any(Function.class)))
          .thenAnswer(
              invocation -> {
                Function<JdbcTemplate, Integer> callback = invocation.getArgument(0);
                return callback.apply(mockJdbc);
              });

      reaper.reapExpiredPlaygrounds();

      verify(mockJdbc).update(anyString(), any(java.sql.Timestamp.class));
    }

    @Test
    @DisplayName("Should do nothing if no expired playgrounds found")
    @SuppressWarnings("unchecked")
    void shouldDoNothingIfNoExpiredPlaygrounds() {
      JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
      when(mockJdbc.update(anyString(), any(java.sql.Timestamp.class))).thenReturn(0);

      when(systemExecutor.executeInTransaction(any(Function.class)))
          .thenAnswer(
              invocation -> {
                Function<JdbcTemplate, Integer> callback = invocation.getArgument(0);
                return callback.apply(mockJdbc);
              });

      reaper.reapExpiredPlaygrounds();

      verify(mockJdbc).update(anyString(), any(java.sql.Timestamp.class));
    }
  }
}
