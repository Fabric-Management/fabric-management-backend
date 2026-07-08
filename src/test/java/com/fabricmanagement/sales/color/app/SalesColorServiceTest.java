package com.fabricmanagement.sales.color.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.color.api.query.ColorQueryService;
import com.fabricmanagement.production.masterdata.color.api.query.ColorQueryService.ColorReference;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesColorServiceTest {

  @Mock private ColorQueryService colorQueryService;

  @InjectMocks private SalesColorService salesColorService;

  @Test
  @DisplayName("Should resolve active color as quote line snapshot")
  void shouldResolveActiveColorAsSnapshot() {
    UUID colorId = UUID.randomUUID();
    when(colorQueryService.findReferenceById(colorId))
        .thenReturn(Optional.of(new ColorReference(colorId, "NAVY-01", "Navy", "#001F3F", true)));

    SalesColorSnapshot snapshot = salesColorService.resolveNewSelectionSnapshot(colorId);

    assertEquals(colorId, snapshot.id());
    assertEquals("NAVY-01", snapshot.code());
    assertEquals("Navy", snapshot.name());
    assertEquals("#001F3F", snapshot.colorHex());
  }

  @Test
  @DisplayName("Should reject newly selected inactive color with explicit domain error")
  void shouldRejectNewlySelectedInactiveColor() {
    UUID colorId = UUID.randomUUID();
    when(colorQueryService.findReferenceById(colorId))
        .thenReturn(Optional.of(new ColorReference(colorId, "NAVY-01", "Navy", "#001F3F", false)));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class,
            () -> salesColorService.resolveNewSelectionSnapshot(colorId));

    assertEquals("SALES_015_REFERENCE_NO_LONGER_AVAILABLE", ex.getErrorCode());
    assertEquals(409, ex.getHttpStatus());
  }

  @Test
  @DisplayName("Should return 404 for unknown or cross-tenant color")
  void shouldReturn404ForUnknownColor() {
    UUID colorId = UUID.randomUUID();
    when(colorQueryService.findReferenceById(colorId)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class, () -> salesColorService.resolveNewSelectionSnapshot(colorId));
  }

  @Test
  @DisplayName("Should accept existing inactive color echo on full-state update")
  void shouldAcceptExistingInactiveColorEchoOnFullStateUpdate() {
    UUID colorId = UUID.randomUUID();
    QuoteLine line = new QuoteLine();
    line.applyColor(colorId, "NAVY-01", "Navy", "#001F3F");

    SalesColorSnapshot snapshot = salesColorService.resolveUpdateSnapshot(colorId, line);

    assertEquals(colorId, snapshot.id());
    assertEquals("NAVY-01", snapshot.code());
    assertEquals("Navy", snapshot.name());
    assertEquals("#001F3F", snapshot.colorHex());
    verifyNoInteractions(colorQueryService);
  }
}
