package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlanOperation;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitCutPlanException;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos.View;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutPlanOperationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockUnitCutPlanOperations {
  private final StockUnitCutPlanOperationRepository operations;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;

  public String hash(Object body) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)));
    } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
      throw new IllegalStateException("Cannot fingerprint cut command", failure);
    }
  }

  public View replay(UUID operation, UUID actor, UUID line, String hash) {
    entityManager
        .createNativeQuery(
            "select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) held")
        .setParameter(
            "key", TenantContext.requireTenantId() + ":CUT_OPERATION:" + line + ":" + operation)
        .getSingleResult();
    var found = operations.findByTenantIdAndOperationId(TenantContext.requireTenantId(), operation);
    if (found.isEmpty()) return null;
    var row = found.get();
    if (!row.getActorId().equals(actor)
        || !row.getTargetLineId().equals(line)
        || !row.getRequestHash().equals(hash))
      throw StockUnitCutPlanException.conflict(
          "IDEMPOTENCY_KEY_REUSED", "The operation belongs to a different cut command");
    try {
      return mapper.readValue(row.getResultJson(), View.class);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Cannot read cut receipt", failure);
    }
  }

  public View complete(UUID operation, UUID actor, UUID line, String hash, View result) {
    try {
      operations.saveAndFlush(
          StockUnitCutPlanOperation.record(
              operation, actor, line, hash, mapper.writeValueAsString(result)));
      return result;
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Cannot write cut receipt", failure);
    }
  }
}
