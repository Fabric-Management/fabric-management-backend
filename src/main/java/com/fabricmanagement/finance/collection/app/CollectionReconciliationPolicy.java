package com.fabricmanagement.finance.collection.app;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How much work a reconciliation run may do, and how often each order is read in full.
 *
 * <p>A run compares a cheap version stamp per agreed order with its projection and reads only the
 * orders that changed. That is exact as long as every change of an order bumps its version; the
 * periodic full read is the net for the day some path forgets to. The net is staggered by how
 * active the order is: a projection whose source changed within {@code cold-after-days} is
 * <em>hot</em> and is read in full every {@code full-sweep-ms}; an older one is <em>cold</em> and
 * is read every {@code cold-sweep-ms}, and its check time is not rewritten on every run.
 *
 * <p>A run reads at most {@code read-budget} orders per tenant, changed ones first. What does not
 * fit stays due and is taken by the next run, so no run grows with the tenant and every tenant gets
 * its turn. {@code full-sweep-ms <= 0} makes every order due on every run.
 */
@Component
public class CollectionReconciliationPolicy {
  private final int readBudget;
  private final Duration hotEvery;
  private final Duration coldEvery;
  private final Duration coldAfter;

  public CollectionReconciliationPolicy(
      @Value("${finance.collections.read-budget:500}") int readBudget,
      @Value("${finance.collections.full-sweep-ms:3600000}") long hotEveryMillis,
      @Value("${finance.collections.cold-sweep-ms:21600000}") long coldEveryMillis,
      @Value("${finance.collections.cold-after-days:30}") long coldAfterDays) {
    this.readBudget = Math.max(1, readBudget);
    this.hotEvery = Duration.ofMillis(Math.max(0, hotEveryMillis));
    Duration cold = Duration.ofMillis(Math.max(0, coldEveryMillis));
    this.coldEvery = cold.compareTo(hotEvery) < 0 ? hotEvery : cold;
    this.coldAfter = Duration.ofDays(Math.max(0, coldAfterDays));
  }

  /** The most orders one run reads in full for one tenant. */
  public int readBudget() {
    return readBudget;
  }

  public Tiers tiers(Instant now) {
    return new Tiers(now.minus(coldAfter), now.minus(hotEvery), now.minus(coldEvery));
  }

  /**
   * The three instants a run judges every projection by.
   *
   * @param hotSince a projection whose source changed at or after this is hot
   * @param hotReadBefore a hot projection last read in full at or before this is due again
   * @param coldReadBefore a cold projection last read in full at or before this is due again
   */
  public record Tiers(Instant hotSince, Instant hotReadBefore, Instant coldReadBefore) {}
}
