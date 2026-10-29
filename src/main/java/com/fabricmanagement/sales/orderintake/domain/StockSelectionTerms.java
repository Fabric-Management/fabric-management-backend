package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Stock eligibility terms, separate from historical customer acceptance and commercial price. */
public final class StockSelectionTerms {
  private StockSelectionTerms() {}

  public static boolean matchesRequest(
      SalesOrder order,
      SalesOrderLine line,
      com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest request) {
    return request != null
        && Objects.equals(request.productId(), line.getProductId())
        && Objects.equals(request.colorId(), line.getColorId())
        && plain(request.finishedWidth()).equals(plain(line.getFinishedWidth()))
        && upper(request.finishedWidthUnit()).equals(upper(line.getFinishedWidthUnit()))
        && Objects.equals(request.customerId(), order.getTradingPartnerId())
        && upper(request.unit()).equals(upper(line.getUnit()))
        && plain(request.requestedQty()).equals(plain(line.getCustomerRequestQty()))
        && plain(request.toleranceUpPct()).equals(plain(line.getToleranceUpPct()))
        && plain(request.toleranceDownPct()).equals(plain(line.getToleranceDownPct()))
        && Boolean.TRUE.equals(request.singleLotRequired()) == line.isSingleLotRequired()
        && (request.quantityPolicy() == null
            || request.quantityPolicy() == line.getQuantityPolicy());
  }

  public static String fingerprint(SalesOrder order, SalesOrderLine line) {
    String terms =
        String.join(
            "|",
            Objects.toString(order.getTradingPartnerId(), ""),
            Objects.toString(line.getProductId(), ""),
            Objects.toString(line.getColorId(), ""),
            plain(line.getFinishedWidth()),
            upper(line.getFinishedWidthUnit()),
            upper(line.getUnit()),
            plain(line.getRequestedQty()),
            plain(line.getCustomerRequestQty()),
            plain(line.getToleranceUpPct()),
            plain(line.getToleranceDownPct()),
            Boolean.toString(line.isSingleLotRequired()));
    if (line.getQuantityPolicy()
        != com.fabricmanagement.sales.common.domain.SalesQuantityPolicy.UNRECORDED)
      terms += "|" + line.getQuantityPolicy().name();
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(terms.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static String plain(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }

  private static String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
