package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "collection_command_receipt", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionCommandReceipt extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID operationId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestHash;

  @Column(nullable = false, updatable = false, length = 30)
  private String resourceType;

  @Column(nullable = false, updatable = false)
  private UUID resourceId;

  @Column(nullable = false, updatable = false)
  private long resourceVersion;

  @Column(updatable = false)
  private UUID paymentId;

  @Type(JsonType.class)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<UUID> matchIds;

  @Override
  protected String getModuleCode() {
    return "CRCP";
  }

  public static CollectionCommandReceipt create(
      UUID operation,
      UUID actor,
      String hash,
      String type,
      UUID resource,
      long version,
      UUID payment,
      List<UUID> matches) {
    var value = new CollectionCommandReceipt();
    value.operationId = operation;
    value.actorId = actor;
    value.requestHash = hash;
    value.resourceType = type;
    value.resourceId = resource;
    value.resourceVersion = version;
    value.paymentId = payment;
    value.matchIds = List.copyOf(matches);
    return value;
  }

  public void requireReplay(UUID actor, String hash) {
    if (!actorId.equals(actor) || !requestHash.equals(hash))
      throw CollectionRuleException.conflict(
          "COLLECTION_OPERATION_REUSED",
          "The operation id belongs to a different command or actor");
  }
}
