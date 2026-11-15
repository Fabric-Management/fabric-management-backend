package com.fabricmanagement.platform.tradingpartner.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A person or company that signs cheques a customer hands over as its own (PAYMENT-PLAN-7 R13): a
 * partner of the customer drawing personally, or a related company. The customer itself is always a
 * drawer and is never stored as a row. A drawer is never deleted; it is deactivated, and cheques
 * keep the name they were received with.
 */
@Entity
@Table(name = "trading_partner_cheque_drawer", schema = "common_company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingPartnerChequeDrawer extends BaseEntity {

  public static final int MAX_NAME_LENGTH = 200;

  @Column(name = "partner_id", nullable = false, updatable = false)
  private UUID partnerId;

  @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
  private String name;

  @Column(name = "normalized_name", nullable = false, length = MAX_NAME_LENGTH)
  private String normalizedName;

  @Enumerated(EnumType.STRING)
  @Column(name = "drawer_type", nullable = false, length = 20)
  private ChequeDrawerType drawerType;

  public static TradingPartnerChequeDrawer create(
      UUID partnerId, String name, ChequeDrawerType type) {
    var drawer = new TradingPartnerChequeDrawer();
    drawer.partnerId = partnerId;
    drawer.change(name, type);
    return drawer;
  }

  /** Renames or retypes the drawer; cheques already received keep their own name snapshot. */
  public void change(String newName, ChequeDrawerType type) {
    if (type == null) throw new IllegalArgumentException("A drawer type is required");
    this.name = displayName(newName);
    this.normalizedName = normalise(newName);
    this.drawerType = type;
  }

  /** Leaves the picker; the row and every cheque that names it stay. */
  public void deactivate() {
    setIsActive(false);
  }

  public void reactivate() {
    setIsActive(true);
  }

  public boolean isUsable() {
    return Boolean.TRUE.equals(getIsActive());
  }

  /**
   * As shown: Unicode-compatibility normalised (NFKC), trimmed, and every run of spaces, including
   * non-breaking ones pasted from spreadsheets, collapsed to one space.
   */
  public static String displayName(String value) {
    if (value == null) throw new IllegalArgumentException("A drawer name is required");
    String shown =
        SPACES.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)).replaceAll(" ").strip();
    if (shown.isEmpty()) throw new IllegalArgumentException("A drawer name is required");
    if (shown.length() > MAX_NAME_LENGTH)
      throw new IllegalArgumentException("A drawer name is at most 200 characters");
    return shown;
  }

  /**
   * The comparison key: the display form without case. Dotted and dotless I (I, İ, ı, i) all
   * compare as "i" whatever the language, so the same name typed with either keyboard is one
   * drawer, and the key is never longer than the name.
   */
  public static String normalise(String value) {
    String lower = displayName(value).toLowerCase(Locale.ROOT);
    return lower.replace(COMBINING_DOT_ABOVE, "").replace('\u0131', 'i');
  }

  private static final java.util.regex.Pattern SPACES =
      java.util.regex.Pattern.compile("[\\s\\p{Z}]+");
  private static final String COMBINING_DOT_ABOVE = "\u0307";

  @Override
  protected String getModuleCode() {
    return "TPCD";
  }
}
