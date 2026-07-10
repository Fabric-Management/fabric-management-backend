package com.fabricmanagement.production.masterdata.color.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorDomainException;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ColorServiceTest {

  @Mock private ColorRepository colorRepository;

  @InjectMocks private ColorService colorService;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should create normalized tenant color card")
  void shouldCreateNormalizedTenantColorCard() {
    when(colorRepository.existsByTenantIdAndCode(tenantId, "NAVY-01")).thenReturn(false);
    when(colorRepository.save(any(Color.class))).thenAnswer(inv -> inv.getArgument(0));

    Color color = colorService.create(" navy-01 ", " Navy ", "#001f3f");

    assertEquals(tenantId, color.getTenantId());
    assertEquals("NAVY-01", color.getCode());
    assertEquals("Navy", color.getName());
    assertEquals("#001F3F", color.getColorHex());
  }

  @Test
  @DisplayName("Should reject duplicate color code within tenant")
  void shouldRejectDuplicateColorCodeWithinTenant() {
    when(colorRepository.existsByTenantIdAndCode(tenantId, "NAVY-01")).thenReturn(true);

    ColorDomainException ex =
        assertThrows(
            ColorDomainException.class, () -> colorService.create("navy-01", "Navy", "#001F3F"));

    assertEquals("PRODUCTION_COLOR_DUPLICATE_CODE", ex.getErrorCode());
    assertEquals(409, ex.getHttpStatus());
  }

  @Test
  @DisplayName("Should deactivate color card with soft delete")
  void shouldDeactivateColorCardWithSoftDelete() {
    UUID colorId = UUID.randomUUID();
    Color color = Color.create(tenantId, "NAVY-01", "Navy", "#001F3F");
    color.setId(colorId);
    when(colorRepository.findByTenantIdAndId(tenantId, colorId)).thenReturn(Optional.of(color));
    when(colorRepository.save(any(Color.class))).thenAnswer(inv -> inv.getArgument(0));

    colorService.deactivate(colorId);

    ArgumentCaptor<Color> captor = ArgumentCaptor.forClass(Color.class);
    verify(colorRepository).save(captor.capture());
    assertFalse(Boolean.TRUE.equals(captor.getValue().getIsActive()));
  }

  @Test
  @DisplayName("Should reactivate a deactivated color card and clear its deletion timestamp")
  void shouldReactivateDeactivatedColorCard() {
    UUID colorId = UUID.randomUUID();
    Color color = Color.create(tenantId, "NAVY-01", "Navy", "#001F3F");
    color.setId(colorId);
    color.delete();
    when(colorRepository.findByTenantIdAndId(tenantId, colorId)).thenReturn(Optional.of(color));
    when(colorRepository.save(any(Color.class))).thenAnswer(inv -> inv.getArgument(0));

    Color reactivated = colorService.activate(colorId);

    assertTrue(Boolean.TRUE.equals(reactivated.getIsActive()));
    assertNull(reactivated.getDeletedAt());
  }

  @Test
  @DisplayName("Should reject activating an unknown color card")
  void shouldRejectActivatingUnknownColorCard() {
    UUID colorId = UUID.randomUUID();
    when(colorRepository.findByTenantIdAndId(tenantId, colorId)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> colorService.activate(colorId));
  }

  @Test
  @DisplayName("Should treat activation of an already active color card as a no-op")
  void shouldTreatActivationOfActiveColorCardAsNoOp() {
    UUID colorId = UUID.randomUUID();
    Color color = Color.create(tenantId, "NAVY-01", "Navy", "#001F3F");
    color.setId(colorId);
    when(colorRepository.findByTenantIdAndId(tenantId, colorId)).thenReturn(Optional.of(color));
    when(colorRepository.save(any(Color.class))).thenAnswer(inv -> inv.getArgument(0));

    Color reactivated = colorService.activate(colorId);

    assertTrue(Boolean.TRUE.equals(reactivated.getIsActive()));
    assertNull(reactivated.getDeletedAt());
  }
}
