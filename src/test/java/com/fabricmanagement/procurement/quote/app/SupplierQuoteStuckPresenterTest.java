package com.fabricmanagement.procurement.quote.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.StuckEventPresentation;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupplierQuoteStuckPresenterTest {

  @Mock private SupplierQuoteRepository quoteRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private SupplierQuoteStuckPresenter presenter;

  @BeforeEach
  void setUp() {
    presenter = new SupplierQuoteStuckPresenter(quoteRepository);
  }

  @Test
  void presentsKnownQuoteWithReferenceAndAffectedUser() {
    UUID tenantId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    UUID createdBy = UUID.randomUUID();
    SupplierQuote quote = quote(quoteId, "SQ-1042", createdBy);
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));

    StuckEventPresentation result = presenter.present(tenantId, payload(quoteId));

    assertThat(result.entityType()).isEqualTo("SUPPLIER_QUOTE");
    assertThat(result.entityId()).isEqualTo(quoteId);
    assertThat(result.entityRef()).isEqualTo("SQ-1042");
    assertThat(result.summary())
        .isEqualTo("Purchase order creation for quote SQ-1042 did not complete.");
    assertThat(result.referenceType()).isEqualTo("SUPPLIER_QUOTE");
    assertThat(result.referenceId()).isEqualTo(quoteId);
    assertThat(result.affectedUserId()).isEqualTo(createdBy);
  }

  @Test
  void omitsSystemUserFromAffectedUser() {
    UUID tenantId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    SupplierQuote quote = quote(quoteId, "SQ-1043", SystemUser.ID);
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));

    StuckEventPresentation result = presenter.present(tenantId, payload(quoteId));

    assertThat(result.affectedUserId()).isNull();
  }

  @Test
  void missingQuoteFallsBackToPayloadOnlyPresentationWithoutThrowing() {
    UUID tenantId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.empty());

    assertThatCode(() -> presenter.present(tenantId, payload(quoteId))).doesNotThrowAnyException();
    StuckEventPresentation result = presenter.present(tenantId, payload(quoteId));

    assertThat(result.entityType()).isEqualTo("SUPPLIER_QUOTE");
    assertThat(result.entityId()).isEqualTo(quoteId);
    assertThat(result.entityRef()).isNull();
    assertThat(result.summary())
        .isEqualTo("Purchase order creation for a supplier quote did not complete.");
    assertThat(result.affectedUserId()).isNull();
  }

  private SupplierQuote quote(UUID quoteId, String quoteNumber, UUID createdBy) {
    SupplierQuote quote = new SupplierQuote();
    quote.setId(quoteId);
    quote.setQuoteNumber(quoteNumber);
    quote.setCreatedBy(createdBy);
    return quote;
  }

  private JsonNode payload(UUID quoteId) {
    return objectMapper.createObjectNode().put("quoteId", quoteId.toString());
  }
}
