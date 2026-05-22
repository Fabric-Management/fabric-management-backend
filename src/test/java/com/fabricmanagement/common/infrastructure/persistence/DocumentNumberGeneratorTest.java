package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentNumberGeneratorTest {

  @Mock private EntityManager entityManager;

  @Mock private Query query;

  @Captor private ArgumentCaptor<String> sqlCaptor;

  @InjectMocks private DocumentNumberGenerator documentNumberGenerator;

  private final UUID tenantId = UUID.randomUUID();
  private final LocalDate testDate = LocalDate.of(2026, 5, 21);

  @BeforeEach
  void setUp() {
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(eq("tenantId"), any(UUID.class))).thenReturn(query);
    when(query.setParameter(eq("documentType"), anyString())).thenReturn(query);
    when(query.setParameter(eq("prefix"), anyString())).thenReturn(query);
  }

  @Test
  void shouldGenerateFormattedDocumentNumber() {
    // Arrange
    when(query.getSingleResult()).thenReturn(1L);

    // Act
    String result = documentNumberGenerator.generate(tenantId, "TEST_DOC", "TD", testDate, 5);

    // Assert
    assertThat(result).isEqualTo("TD-20260521-00001");
    verify(query).setParameter("tenantId", tenantId);
    verify(query).setParameter("documentType", "TEST_DOC");
    verify(query).setParameter("prefix", "TD-20260521-");
    verify(entityManager).createNativeQuery(sqlCaptor.capture());

    String executedSql = sqlCaptor.getValue();
    assertThat(executedSql).contains("INSERT INTO common_infrastructure.document_sequence");
    assertThat(executedSql).contains("RETURNING next_val");
  }

  @Test
  void shouldPadWithGivenWidth() {
    // Arrange
    when(query.getSingleResult()).thenReturn(42L);

    // Act
    String result = documentNumberGenerator.generate(tenantId, "TEST_DOC", "TD", testDate, 3);

    // Assert
    assertThat(result).isEqualTo("TD-20260521-042");
  }

  @Test
  void shouldNotPadIfSequenceExceedsWidth() {
    // Arrange
    when(query.getSingleResult()).thenReturn(1234L);

    // Act
    String result = documentNumberGenerator.generate(tenantId, "TEST_DOC", "TD", testDate, 3);

    // Assert
    assertThat(result).isEqualTo("TD-20260521-1234");
  }
}
