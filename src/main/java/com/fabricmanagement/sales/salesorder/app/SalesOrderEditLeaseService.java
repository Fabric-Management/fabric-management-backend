package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseProperties;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService.Acquisition;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService.Granted;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService.Refused;
import com.fabricmanagement.platform.realtime.domain.LiveEditLease;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseKey;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseMode;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos.Holder;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos.Lease;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos.Policy;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseKey;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository.LiveRevisionView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field leases of a sales order (CEDIT-07 §4): acquire, renew, release and list for the open edit
 * form of one browser tab (its CEDIT-06 edit session). Access is decided again on every call
 * ({@link SalesOrderEditAccess}); acquiring and renewing also need the order to be editable now.
 * The platform engine decides ownership; this adapter maps the order's key catalogue, checks that a
 * line key names an active line of this order, and names the order's edit epoch, so a lease never
 * outlives the order's leaving the draft.
 *
 * <p>Acquire takes the order row in share mode first, the same first lock as every writer of the
 * order, so the editability, the epoch and the lines it decides on are the committed ones and no
 * writer of the order commits between that decision and the grant.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderEditLeaseService {

  static final String EDIT_LEASE_UNAVAILABLE = "EDIT_LEASE_UNAVAILABLE";
  static final String EDIT_LEASE_KEY_INVALID = "EDIT_LEASE_KEY_INVALID";

  private final SalesOrderEditAccess access;
  private final SalesOrderRevision revision;
  private final SalesOrderLineRepository lines;
  private final LiveEditLeaseService leases;

  /** Grants every key to the caller's edit session, or none (409 with the holders in the way). */
  @Transactional
  public SalesOrderEditLeaseDtos.Grant acquire(
      UUID orderId, SalesOrderEditLeaseDtos.AcquireRequest request, UUID actor) {
    SalesOrder order = access.requireWritable(orderId, actor);
    revision.lockShared(order);
    if (!Boolean.TRUE.equals(order.getIsActive())) {
      throw SalesOrderEditAccess.notFound(orderId);
    }
    SalesOrderEditBases.assertEditable(order);
    List<LiveLeaseKey> keys = checkedKeys(order, request.keys());
    Acquisition answer =
        leases.acquire(
            resource(orderId), order.getEditEpoch(), request.editSessionId(), actor, keys);
    return switch (answer) {
      case Granted granted ->
          new SalesOrderEditLeaseDtos.Grant(
              granted.leases().stream().map(SalesOrderEditLeaseService::own).toList(), policy());
      case Refused refused ->
          throw OrderDomainException.conflict(
                  EDIT_LEASE_UNAVAILABLE,
                  "Someone else is editing this now; you can read it and edit other fields")
              .withDetail("holders", holders(refused.holders(), actor));
    };
  }

  /**
   * Extends the caller's own leases named by their tokens, each independently: what this tab still
   * holds and the tokens it lost. Needs current write access and an editable order.
   *
   * <p>Like acquire, it takes the order row in share mode first (CEDIT-07 R1) and decides on the
   * committed editability and edit epoch: a transition that left the draft before it is seen (the
   * renewal is refused, and back in the draft the old token is lost), and a transition after it
   * waits for the renewal and then voids the lease. Order row, then session, then lease rows.
   */
  @Transactional
  public SalesOrderEditLeaseDtos.Renewal renew(
      UUID orderId, SalesOrderEditLeaseDtos.TokensRequest request, UUID actor) {
    SalesOrder order = access.requireWritable(orderId, actor);
    revision.lockShared(order);
    if (!Boolean.TRUE.equals(order.getIsActive())) {
      throw SalesOrderEditAccess.notFound(orderId);
    }
    SalesOrderEditBases.assertEditable(order);
    LiveEditLeaseService.Renewal renewal =
        leases.renew(
            resource(orderId),
            order.getEditEpoch(),
            request.editSessionId(),
            actor,
            request.leaseTokens());
    return new SalesOrderEditLeaseDtos.Renewal(
        renewal.renewed().stream().map(SalesOrderEditLeaseService::own).toList(),
        renewal.lost(),
        policy());
  }

  /**
   * Gives back the caller's own leases; needs only read access, so a person who lost write access
   * can still clean up. Never touches anyone else's lease.
   */
  @Transactional
  public void release(UUID orderId, SalesOrderEditLeaseDtos.TokensRequest request, UUID actor) {
    access.requireReadable(orderId, actor);
    leases.release(resource(orderId), request.editSessionId(), actor, request.leaseTokens());
  }

  /** Who holds which keys of the order now, with the lease policy; no token. */
  @Transactional(readOnly = true)
  public SalesOrderEditLeaseDtos.Leases list(UUID orderId, UUID actor) {
    LiveRevisionView order = access.requireReadable(orderId, actor);
    return new SalesOrderEditLeaseDtos.Leases(
        holders(leases.held(resource(orderId), epoch(order)), actor), policy());
  }

  /** The lease policy of the bound tenant, as published with every answer. */
  Policy policy() {
    LiveEditLeaseProperties properties = leases.properties();
    LiveLeaseMode mode = leases.mode(SalesOrderLiveRevisionSource.RESOURCE_TYPE);
    return new Policy(
        SalesOrderEditLeaseDtos.Mode.valueOf(mode.name()),
        properties.ttlSeconds(),
        properties.renewAfterSeconds(),
        properties.idleAfterSeconds(),
        properties.idleWarningSeconds(),
        properties.getMaxKeysPerRequest(),
        properties.getMaxLeasesPerSession());
  }

  /** Others' (and the caller's other tabs') leases as anyone who may read the order sees them. */
  List<Holder> holders(Collection<LiveEditLease> held, UUID actor) {
    Map<UUID, Optional<String>> names = new HashMap<>();
    return held.stream()
        .map(
            lease -> {
              SalesOrderEditLeaseKey key = SalesOrderLeaseKeys.toWire(lease.key());
              boolean mine = actor.equals(lease.getUserId());
              return new Holder(
                  key.key(),
                  key.lineId(),
                  lease.getUserId(),
                  names.computeIfAbsent(lease.getUserId(), access::displayName).orElse(null),
                  mine,
                  mine ? lease.getSessionId() : null,
                  lease.getAcquiredAt(),
                  lease.getExpiresAt());
            })
        .toList();
  }

  /**
   * The request's keys, checked against the catalogue and the order: a header key names no line, a
   * line key and the whole line name an active line of this order, no key appears twice, and the
   * request stays within the configured bound. A wrong line is refused like an unknown one, without
   * saying whether it exists elsewhere.
   */
  private List<LiveLeaseKey> checkedKeys(SalesOrder order, List<SalesOrderEditLeaseKey> requested) {
    if (requested.size() > leases.properties().getMaxKeysPerRequest()) {
      throw invalidKey(
          "Ask for at most " + leases.properties().getMaxKeysPerRequest() + " keys at once",
          null,
          null);
    }
    Set<UUID> activeLines =
        lines
            .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
                TenantContext.requireTenantId(), order.getId())
            .stream()
            .map(SalesOrderLine::getId)
            .collect(Collectors.toSet());
    Set<LiveLeaseKey> seen = new HashSet<>();
    List<LiveLeaseKey> keys = new ArrayList<>();
    for (SalesOrderEditLeaseKey key : requested) {
      String wire = key.key().wireName();
      if (key.key().isLineScoped()) {
        if (key.lineId() == null) {
          throw invalidKey("A line key names its line", wire, null);
        }
        if (!activeLines.contains(key.lineId())) {
          throw invalidKey("The line is not an active line of this order", wire, key.lineId());
        }
      } else if (key.lineId() != null) {
        throw invalidKey("A header key names no line", wire, key.lineId());
      }
      LiveLeaseKey lease = SalesOrderLeaseKeys.of(key);
      if (!seen.add(lease)) {
        throw invalidKey("A key appears twice", wire, key.lineId());
      }
      keys.add(lease);
    }
    return keys;
  }

  private static OrderDomainException invalidKey(String message, String key, UUID lineId) {
    OrderDomainException failure = OrderDomainException.invalid(EDIT_LEASE_KEY_INVALID, message);
    if (key != null) {
      failure.withDetail("key", key);
    }
    if (lineId != null) {
      failure.withDetail("lineId", lineId);
    }
    return failure;
  }

  private static Lease own(LiveEditLease lease) {
    SalesOrderEditLeaseKey key = SalesOrderLeaseKeys.toWire(lease.key());
    return new Lease(
        key.key(), key.lineId(), lease.getToken(), lease.getAcquiredAt(), lease.getExpiresAt());
  }

  private static long epoch(LiveRevisionView order) {
    Long epoch = order.getEditEpoch();
    if (epoch == null) {
      throw new IllegalStateException("A sales order always has an edit epoch");
    }
    return epoch;
  }

  private static LiveResource resource(UUID orderId) {
    return SalesOrderLiveRevisionSource.resource(orderId);
  }
}
