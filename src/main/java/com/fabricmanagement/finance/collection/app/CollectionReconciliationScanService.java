package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.CollectionReconciliationPolicy.Tiers;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort.SourceStamp;
import com.fabricmanagement.finance.collection.domain.CollectionProjection;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionProjectionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides, without reading any order, what a reconciliation run owes each one.
 *
 * <p>A projection is compared with the stamp the source shows for its order, and with the tier it
 * is in ({@link CollectionReconciliationPolicy}). Projections whose order is no longer listed are
 * found as well, in the same pass over the order-id range, so a run never has to remember every
 * order it has seen.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionReconciliationScanService {
  private static final int PAGE = 200;

  /** What a run owes one order. */
  public enum Verdict {
    /** The source differs from the projection: read it now, ahead of anything merely due. */
    CHANGED,
    /** The source matches, but the order has not been read in full for longer than its tier. */
    DUE,
    /** Matches and read recently, in the hot tier: only its check time is brought forward. */
    CURRENT_HOT,
    /** Matches and read recently, in the cold tier: nothing to do. */
    CURRENT_COLD
  }

  /**
   * One order's verdict. {@code readAt} is when it was last read in full (null if finance has never
   * seen the order), so that among orders that are merely due the longest-waiting are read first.
   */
  public record Finding(UUID orderId, Verdict verdict, Instant readAt) {}

  private final CollectionProjectionRepository projections;
  private final CollectionPaymentMatchRepository matches;

  /**
   * The verdicts for one listed page: its stamps, then any projection that lies within the page's
   * order-id range but was not listed (its source is gone or no longer agreed). Nothing is written
   * and no lock is taken.
   *
   * @param after the order id the page starts after (exclusive)
   * @param stamps the page, in order-id order and not empty
   */
  public List<Finding> triage(UUID after, List<SourceStamp> stamps, Tiers tiers) {
    UUID upTo = stamps.getLast().orderId();
    var known = new HashMap<UUID, CollectionProjection>();
    projections
        .findByTenantIdAndOrderIdGreaterThanAndOrderIdLessThanEqualOrderByOrderId(
            TenantContext.requireTenantId(), after, upTo)
        .forEach(value -> known.put(value.getOrderId(), value));
    var findings = new ArrayList<Finding>(stamps.size());
    for (var stamp : stamps) findings.add(judge(known.remove(stamp.orderId()), stamp, tiers));
    // What is left was in the range but not listed.
    known.values().forEach(value -> findings.add(unlisted(value, tiers)));
    return findings;
  }

  /**
   * Projections after the last listed order, in order-id order, a page at a time. The caller
   * continues from the last one returned; an empty result ends the pass.
   */
  public List<Finding> unlistedAfter(UUID after, Tiers tiers) {
    return projections
        .findByTenantIdAndOrderIdGreaterThanOrderByOrderId(
            TenantContext.requireTenantId(), after, PageRequest.of(0, PAGE))
        .stream()
        .map(value -> unlisted(value, tiers))
        .toList();
  }

  public List<UUID> voidedPayments(UUID after) {
    return matches.voidPaymentsToObserve(
        TenantContext.requireTenantId(), after, PageRequest.of(0, PAGE));
  }

  private static Finding judge(CollectionProjection projection, SourceStamp stamp, Tiers tiers) {
    if (projection == null) return new Finding(stamp.orderId(), Verdict.CHANGED, null);
    Verdict verdict = moved(projection, stamp) ? Verdict.CHANGED : tier(projection, tiers);
    return new Finding(stamp.orderId(), verdict, projection.getSourceReadAt());
  }

  /**
   * A projection with no source: the first time it is seen so, it must be read so that the loss is
   * recorded; after that it is only looked at again when its tier says so.
   */
  private static Finding unlisted(CollectionProjection projection, Tiers tiers) {
    Verdict verdict = projection.isSourceMissing() ? tier(projection, tiers) : Verdict.CHANGED;
    return new Finding(projection.getOrderId(), verdict, projection.getSourceReadAt());
  }

  /**
   * Whether the source says something the projection does not: another revision, plan version or
   * cancellation, an order edited since, or the source having been missing.
   */
  private static boolean moved(CollectionProjection projection, SourceStamp stamp) {
    return projection.isSourceMissing()
        || !Objects.equals(projection.getRevisionId(), stamp.revisionId())
        || projection.getSourceVersion() != stamp.planVersion()
        || projection.isCancelled() != stamp.cancelled()
        || projection.getSourceOrderVersion() != stamp.orderVersion();
  }

  /** A projection that matches its source is due, or current in the tier its activity gives it. */
  private static Verdict tier(CollectionProjection projection, Tiers tiers) {
    boolean cold = projection.getSourceChangedAt().isBefore(tiers.hotSince());
    Instant readBefore = cold ? tiers.coldReadBefore() : tiers.hotReadBefore();
    if (!projection.getSourceReadAt().isAfter(readBefore)) return Verdict.DUE;
    return cold ? Verdict.CURRENT_COLD : Verdict.CURRENT_HOT;
  }
}
