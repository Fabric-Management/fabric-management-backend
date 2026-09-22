package com.fabricmanagement.flowboard.decision.app;

import com.fabricmanagement.flowboard.decision.dto.DecisionProjectionRebuildResponse;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionSubjectProjectionWriter;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DecisionProjectionRebuildService {
  private static final int BATCH = 100;
  private final OrderCoverProjectionPort source;
  private final DecisionSubjectProjectionWriter writer;
  private final DecisionProjectionRebuildState state;

  public DecisionProjectionRebuildResponse rebuild(UUID tenant, Collection<UUID> selectedCaseIds) {
    state.started(tenant);
    try {
      return selectedCaseIds == null || selectedCaseIds.isEmpty()
          ? full(tenant)
          : apply(tenant, List.copyOf(selectedCaseIds));
    } finally {
      state.finished(tenant);
    }
  }

  private DecisionProjectionRebuildResponse full(UUID tenant) {
    Counters counters = new Counters();
    // Freeze the orphan candidates before scanning the source. A projection created by an event
    // while the rebuild is running must never be mistaken for an orphan and deleted afterwards.
    List<UUID> orphanCandidates = writer.caseIdsSnapshot(tenant, BATCH);

    UUID cursor = null;
    while (true) {
      List<UUID> ids = source.caseIdsAfter(tenant, cursor, BATCH);
      if (ids.isEmpty()) break;
      counters.add(apply(tenant, ids));
      cursor = ids.getLast();
      if (ids.size() < BATCH) break;
    }

    for (int start = 0; start < orphanCandidates.size(); start += BATCH) {
      List<UUID> batch =
          orphanCandidates.subList(start, Math.min(start + BATCH, orphanCandidates.size()));
      Set<UUID> existing = new HashSet<>();
      source.facts(tenant, batch).forEach(fact -> existing.add(fact.caseId()));
      for (UUID id : batch) {
        if (!existing.contains(id)) counters.orphaned += writer.delete(tenant, id);
      }
    }
    return counters.response();
  }

  private DecisionProjectionRebuildResponse apply(UUID tenant, Collection<UUID> ids) {
    Counters counters = new Counters();
    var facts = source.facts(tenant, ids);
    counters.scanned += facts.size();
    Set<UUID> found = new HashSet<>();
    for (var fact : facts) {
      found.add(fact.caseId());
      switch (writer.apply(fact, null)) {
        case INSERTED -> counters.inserted++;
        case UPDATED -> counters.updated++;
        case SKIPPED -> counters.unchanged++;
      }
    }
    for (UUID requested : ids) {
      if (!found.contains(requested)) counters.orphaned += writer.delete(tenant, requested);
    }
    return counters.response();
  }

  private static final class Counters {
    long scanned;
    long inserted;
    long updated;
    long unchanged;
    long orphaned;

    void add(DecisionProjectionRebuildResponse value) {
      scanned += value.scanned();
      inserted += value.inserted();
      updated += value.updated();
      unchanged += value.unchanged();
      orphaned += value.orphaned();
    }

    DecisionProjectionRebuildResponse response() {
      return new DecisionProjectionRebuildResponse(scanned, inserted, updated, unchanged, orphaned);
    }
  }
}
