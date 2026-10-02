package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * The line terms a customer acceptance covers (SOI A11, IK-13): product, colour, finished width,
 * unit, quantity, the single-lot condition and the price. When any of them changes after the
 * acceptance, the acceptance no longer covers the line and must be taken again. Internal notes and
 * delivery wishes are not part of it.
 */
public final class AcceptanceTerms {

  private AcceptanceTerms() {}

  public static String fingerprint(SalesOrderLine line) {
    // The stored price (four decimals), not Money, which rounds to the currency's minor unit.
    BigDecimal price = line.getUnitPriceAmount();
    String canonical =
        String.join(
            "|",
            Objects.toString(line.getProductId(), ""),
            Objects.toString(line.getColorId(), ""),
            plain(line.getFinishedWidth()),
            upper(line.getFinishedWidthUnit()),
            upper(line.getUnit()),
            plain(line.getRequestedQty()),
            Boolean.toString(line.isSingleLotRequired()),
            price == null ? "" : plain(price),
            price == null || line.getCurrency() == null ? "" : line.getCurrency());
    return sha256(canonical);
  }

  private static String plain(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }

  private static String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
