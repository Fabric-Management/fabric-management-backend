package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fingerprint of every fact the options depend on (STOCK-PROPOSAL-2): pieces, lots and their
 * average piece, compatibility evidence, what quotes hold and the lot-health rule. A change makes
 * an earlier proposal stale.
 */
final class EvidenceFingerprint {

  private EvidenceFingerprint() {}

  static String of(
      QuantityEvaluator.Input input, List<ProposalLot> lots, LotHealthPolicy lotHealth) {
    StringBuilder text = new StringBuilder();
    text.append(input.canonicalTarget().stripTrailingZeros().toPlainString()).append('|');
    text.append(input.singleLotRequired()).append('|');
    lots.forEach(
        lot -> {
          text.append(lot.batchId())
              .append('#')
              .append(lot.widthEvidence())
              .append('#')
              .append(lot.productionDate())
              .append('#')
              .append(
                  lot.averagePiece() == null
                      ? "-"
                      : lot.averagePiece().stripTrailingZeros().toPlainString())
              .append(';');
          lot.pieces().stream()
              .sorted(Comparator.comparing(ProposalPiece::stockUnitId))
              .forEach(
                  piece ->
                      text.append(piece.stockUnitId())
                          .append(':')
                          .append(piece.version())
                          .append(':')
                          .append(piece.state())
                          .append(':')
                          .append(
                              piece.canonicalMeasure() == null
                                  ? "-"
                                  : piece.canonicalMeasure().stripTrailingZeros().toPlainString())
                          .append(';'));
        });
    input.confirmedGroups().stream()
        .map(EvidenceFingerprint::sortedKey)
        .sorted()
        .forEach(key -> text.append("C").append(key));
    input.toneAcceptedGroups().stream()
        .map(EvidenceFingerprint::sortedKey)
        .sorted()
        .forEach(key -> text.append("T").append(key));
    input.holds().pieceHolds().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                text.append("H")
                    .append(entry.getKey())
                    .append(':')
                    .append(entry.getValue().quoteLineId())
                    .append(':')
                    .append(entry.getValue().verified()));
    input.holds().lotIntents().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                text.append("I")
                    .append(entry.getKey())
                    .append(':')
                    .append(
                        entry.getValue().canonicalTotal() == null
                            ? "?"
                            : entry
                                .getValue()
                                .canonicalTotal()
                                .stripTrailingZeros()
                                .toPlainString())
                    .append(':')
                    .append(entry.getValue().intents().size()));
    // What a lot may keep decides which options exist (2026-10-14): a changed setting makes an
    // earlier proposal stale.
    text.append("K")
        .append(lotHealth.minHealthyPieces())
        .append(':')
        .append(lotHealth.shortPieceRatio().stripTrailingZeros().toPlainString());
    return sha256(text.toString());
  }

  private static String sortedKey(Set<UUID> group) {
    return group.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
  }

  static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
