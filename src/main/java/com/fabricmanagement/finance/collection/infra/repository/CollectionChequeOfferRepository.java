package com.fabricmanagement.finance.collection.infra.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CollectionChequeOfferRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public record Evidence(UUID chequeId, UUID obligationId, UUID actorId, Instant recordedAt) {}

  public Optional<Evidence> find(UUID tenant, UUID cheque) {
    return jdbc
        .query(
            """
            SELECT * FROM finance.collection_cheque_offer
            WHERE tenant_id=:tenant AND cheque_id=:cheque
            """,
            Map.of("tenant", tenant, "cheque", cheque),
            (r, n) -> evidence(r))
        .stream()
        .findFirst();
  }

  public void insert(UUID tenant, Evidence offer) {
    jdbc.update(
        """
        INSERT INTO finance.collection_cheque_offer
          (tenant_id, cheque_id, obligation_id, actor_id, recorded_at)
        VALUES (:tenant, :cheque, :obligation, :actor, :recordedAt)
        """,
        Map.of(
            "tenant", tenant,
            "cheque", offer.chequeId(),
            "obligation", offer.obligationId(),
            "actor", offer.actorId(),
            "recordedAt", Timestamp.from(offer.recordedAt())));
  }

  public boolean hasHeldOffer(UUID tenant, UUID obligation) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT EXISTS (
          SELECT 1 FROM finance.collection_cheque_offer offer
          JOIN finance.collection_cheque c
            ON c.tenant_id=offer.tenant_id AND c.id=offer.cheque_id
          WHERE offer.tenant_id=:tenant AND offer.obligation_id=:obligation
            AND c.is_active AND c.state IN ('RECEIVED','PRESENTED')
            AND NOT EXISTS (
              SELECT 1 FROM finance.collection_cheque_application a
              WHERE a.tenant_id=c.tenant_id AND a.cheque_id=c.id
                AND a.is_active AND a.reversed_at IS NULL
            )
        )
        """,
            Map.of("tenant", tenant, "obligation", obligation),
            Boolean.class));
  }

  static Evidence evidence(ResultSet r) throws SQLException {
    return new Evidence(
        r.getObject("cheque_id", UUID.class),
        r.getObject("obligation_id", UUID.class),
        r.getObject("actor_id", UUID.class),
        r.getTimestamp("recorded_at").toInstant());
  }
}
