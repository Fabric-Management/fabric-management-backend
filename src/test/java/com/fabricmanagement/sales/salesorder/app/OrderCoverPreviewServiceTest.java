package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.salesorder.dto.OrderCoverSelectionPreviewRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class OrderCoverPreviewServiceTest {
  @Test
  void previewOwnsAReadOnlyRepeatableReadBoundary() throws Exception {
    Transactional transaction =
        OrderCoverPreviewService.class
            .getDeclaredMethod(
                "preview", UUID.class, UUID.class, OrderCoverSelectionPreviewRequest.class)
            .getAnnotation(Transactional.class);

    assertThat(transaction).isNotNull();
    assertThat(transaction.readOnly()).isTrue();
    assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
  }
}
