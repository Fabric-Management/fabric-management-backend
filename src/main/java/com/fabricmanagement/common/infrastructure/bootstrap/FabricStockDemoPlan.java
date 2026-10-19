package com.fabricmanagement.common.infrastructure.bootstrap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The playground's fabric stock as plain data: which fabric, colour and finished width has which
 * lots, and how long every roll is. All of it is illustrative and British (root {@code AGENTS.md}).
 *
 * <p>How a mill's rolls really come out, which this plan copies: the winder cuts {@value #ROLL_M} m
 * rolls, so most rolls are exactly that; the end of a lot leaves a remainder that is longer or
 * shorter (135 m, 90 m); and now and then a roll is cut short where a fabric fault was taken out
 * (50-85 m). Lots run from about 500 m to about 2,500 m. The main colour of a fabric has two or
 * three lots at each width; a second colour has one lot at one or both widths, so choosing a colour
 * and a width sometimes finds nothing, as it does in a real order.
 */
final class FabricStockDemoPlan {

  /** The roll length the winder is set to, in metres. */
  static final int ROLL_M = 120;

  /** One fabric with every lot of every colour and width it is stocked in. */
  record Fabric(
      String code, String name, String description, int gsm, String price, List<Lot> lots) {
    Fabric {
      lots = List.copyOf(lots);
    }

    /** The colour a fabric is mainly sold in: its first lot's. */
    String mainColour() {
      return lots.getFirst().colour();
    }
  }

  /**
   * One dye lot of one colour at one finished width. {@code mixable} says the lot is confirmed
   * combinable with the other mixable lots of the same colour and width; an unconfirmed lot may
   * carry a shade difference and is only offered alone.
   */
  record Lot(String suffix, String colour, int widthCm, List<Integer> rolls, boolean mixable) {
    Lot {
      rolls = List.copyOf(rolls);
    }

    int metres() {
      return rolls.stream().mapToInt(Integer::intValue).sum();
    }

    BigDecimal metresDecimal() {
      return BigDecimal.valueOf(metres());
    }

    /** {@code NAVY-01} becomes {@code NAVY}, for the lot code. */
    String colourTag() {
      return colour.substring(0, colour.indexOf('-'));
    }

    /** Rolls under 90 m: cut short where a fabric fault was removed, not a lot-end remainder. */
    long cutRolls() {
      return rolls.stream().filter(length -> length < 90).count();
    }
  }

  static final List<Fabric> FABRICS =
      List.of(
          new Fabric(
              "P1454",
              "PM-1454 Cotton Poplin 120gsm (demo)",
              "Plain-weave cotton poplin with a crisp hand, for shirts and dresses.",
              120,
              "8.60",
              List.of(
                  lot("A", "NAVY-01", 150, true, 14, 135),
                  lot("B", "NAVY-01", 150, true, 6, 90),
                  lot("A", "NAVY-01", 160, true, 11, 90),
                  lot("B", "NAVY-01", 160, true, 4, 74),
                  lot("A", "PFD-00", 160, true, 6, 135))),
          new Fabric(
              "P1455",
              "PM-1455 Cotton Twill 220gsm (demo)",
              "2/1 cotton twill with a firm diagonal face, for workwear and chinos.",
              220,
              "9.20",
              List.of(
                  lot("A", "CHAR-03", 150, true, 9, 90),
                  lot("B", "CHAR-03", 150, true, 5, 135, 58),
                  lot("C", "CHAR-03", 150, false, 4, 52),
                  lot("A", "CHAR-03", 160, true, 12, 135),
                  lot("B", "CHAR-03", 160, true, 5, 90),
                  lot("A", "NAVY-01", 150, true, 5, 90))),
          new Fabric(
              "P1456",
              "PM-1456 Cotton Satin 160gsm (demo)",
              "Cotton sateen with a soft lustre, for bed linen and light trousers.",
              160,
              "11.40",
              List.of(
                  lot("A", "ECRU-02", 150, true, 10, 90),
                  lot("B", "ECRU-02", 150, true, 5, 135),
                  lot("A", "ECRU-02", 160, true, 8, 135),
                  lot("B", "ECRU-02", 160, true, 4, 90, 82),
                  lot("A", "GREIGE-00", 150, true, 5, 90),
                  lot("A", "GREIGE-00", 160, true, 5, 135))),
          new Fabric(
              "P1457",
              "PM-1457 Linen Blend 180gsm (demo)",
              "Linen-cotton blend with a dry, textured hand, for summer shirts.",
              180,
              "12.80",
              List.of(
                  lot("A", "RUST-04", 150, true, 20, 90),
                  lot("B", "RUST-04", 150, true, 7, 135),
                  lot("C", "RUST-04", 150, false, 4, 61),
                  lot("A", "RUST-04", 160, true, 9, 135),
                  lot("B", "RUST-04", 160, true, 5, 90),
                  lot("A", "ECRU-02", 150, true, 6, 90),
                  lot("A", "ECRU-02", 160, true, 5, 135))),
          new Fabric(
              "P1458",
              "PM-1458 Single Jersey 180gsm (demo)",
              "Cotton single jersey knit, finished open width, for T-shirts.",
              180,
              "6.75",
              List.of(
                  lot("A", "IND-05", 150, true, 12, 90),
                  lot("B", "IND-05", 150, true, 5, 135),
                  lot("A", "IND-05", 160, true, 14, 135),
                  lot("B", "IND-05", 160, true, 5, 90, 71),
                  lot("A", "GREIGE-00", 160, true, 5, 90))));

  private FabricStockDemoPlan() {}

  /** {@code full} rolls of {@value #ROLL_M} m, then the odd ones: lot remainder and cut rolls. */
  private static Lot lot(
      String suffix, String colour, int widthCm, boolean mixable, int full, Integer... others) {
    List<Integer> rolls = new ArrayList<>(Collections.nCopies(full, ROLL_M));
    Collections.addAll(rolls, others);
    return new Lot(suffix, colour, widthCm, rolls, mixable);
  }

  /** The lot number, for example {@code PG-FAB-P1454-NAVY-150-A}; unique across the plan. */
  static String lotCode(Fabric fabric, Lot lot) {
    return "PG-FAB-"
        + fabric.code()
        + "-"
        + lot.colourTag()
        + "-"
        + lot.widthCm()
        + "-"
        + lot.suffix();
  }

  /** The lots of one fabric in one colour at one width. */
  static List<Lot> lotsOf(Fabric fabric, String colour, int widthCm) {
    return fabric.lots().stream()
        .filter(lot -> lot.colour().equals(colour) && lot.widthCm() == widthCm)
        .toList();
  }
}
