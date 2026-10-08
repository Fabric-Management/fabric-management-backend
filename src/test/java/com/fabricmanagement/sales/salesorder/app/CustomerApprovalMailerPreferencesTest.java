package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LINE-PREFERENCES-1: the customer reads both line preferences in the version they approve. */
class CustomerApprovalMailerPreferencesTest {

  @Test
  @DisplayName("each line shows its own dye-lot and shipment preference")
  void linesCarryTheirOwnPreferences() {
    OrderVersionContent content =
        content(
            line("Poplin 120", false, LineShipmentPreference.AS_READY),
            line("Twill 240", true, LineShipmentPreference.WHEN_COMPLETE));

    String html = CustomerApprovalMailer.details(content);

    int poplin = html.indexOf("Poplin 120");
    int twill = html.indexOf("Twill 240");
    assertThat(poplin).isNotNegative().isLessThan(twill);
    assertThat(html.substring(poplin, twill))
        .contains("Multiple dye lots allowed")
        .contains("Ships as ready")
        .doesNotContain("Single dye lot")
        .doesNotContain("Ships when all is ready")
        .doesNotContain(CustomerApprovalMailer.SPLIT_SHARE_NOTE);
    assertThat(html.substring(twill))
        .contains("Single dye lot")
        .contains("Ships when all is ready")
        .contains(CustomerApprovalMailer.SPLIT_SHARE_NOTE);
  }

  @Test
  @DisplayName("the defaults read as multiple lots allowed and ships as ready")
  void defaultsAreWordedExplicitly() {
    assertThat(
            CustomerApprovalMailer.preferences(
                line("Poplin 120", false, LineShipmentPreference.AS_READY)))
        .isEqualTo("Multiple dye lots allowed · Ships as ready");
  }

  private static OrderVersionContent.Line line(
      String product, boolean singleLot, LineShipmentPreference shipment) {
    return new OrderVersionContent.Line(
        UUID.randomUUID(),
        product,
        null,
        null,
        null,
        new BigDecimal("1000"),
        "M",
        null,
        null,
        new BigDecimal("4.50"),
        "GBP",
        null,
        null,
        null,
        singleLot,
        shipment);
  }

  private static OrderVersionContent content(OrderVersionContent.Line... lines) {
    return new OrderVersionContent(
        "Bradford Mills",
        "SO-1",
        "Northern Garments",
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(lines),
        List.of(),
        0,
        List.of(),
        null);
  }
}
