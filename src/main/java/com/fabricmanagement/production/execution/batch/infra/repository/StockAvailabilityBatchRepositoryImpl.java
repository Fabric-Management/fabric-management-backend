package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class StockAvailabilityBatchRepositoryImpl implements StockAvailabilityBatchRepository {

  private static final Set<BatchStatus> SALEABLE_BATCH_STATUSES =
      Set.of(BatchStatus.AVAILABLE, BatchStatus.RESERVED);
  private static final Set<StockUnitStatus> SELECTABLE_PIECE_STATUSES =
      Set.of(StockUnitStatus.AVAILABLE, StockUnitStatus.PARTIAL);
  // Volume/count stock needs its own canonical measure contract; do not guess KG or M here.
  private static final Set<ProductType> AVAILABILITY_PRODUCT_TYPES =
      Set.of(ProductType.FABRIC, ProductType.YARN, ProductType.FIBER);

  @PersistenceContext private EntityManager entityManager;

  @Override
  public Page<ProductRow> findAvailabilityProducts(
      UUID tenantId, Filter filter, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<ProductRow> query = cb.createQuery(ProductRow.class);
    Root<Batch> batch = query.from(Batch.class);
    query
        .select(cb.construct(ProductRow.class, batch.get("productId"), batch.get("productType")))
        .where(predicates(cb, query, batch, tenantId, filter, null))
        .groupBy(batch.get("productId"), batch.get("productType"))
        .orderBy(cb.asc(batch.get("productId")));

    List<ProductRow> content = page(entityManager.createQuery(query), pageable).getResultList();

    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<Batch> countBatch = countQuery.from(Batch.class);
    countQuery
        .select(cb.countDistinct(countBatch.get("productId")))
        .where(predicates(cb, countQuery, countBatch, tenantId, filter, null));
    long total = entityManager.createQuery(countQuery).getSingleResult();
    return new PageImpl<>(content, pageable, total);
  }

  @Override
  public Page<Batch> findAvailabilityLots(UUID tenantId, Filter filter, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Batch> query = cb.createQuery(Batch.class);
    Root<Batch> batch = query.from(Batch.class);
    query
        .select(batch)
        .where(predicates(cb, query, batch, tenantId, filter, null))
        .orderBy(cb.asc(batch.get("batchCode")), cb.asc(batch.get("id")));
    List<Batch> content = page(entityManager.createQuery(query), pageable).getResultList();

    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<Batch> countBatch = countQuery.from(Batch.class);
    countQuery
        .select(cb.count(countBatch))
        .where(predicates(cb, countQuery, countBatch, tenantId, filter, null));
    long total = entityManager.createQuery(countQuery).getSingleResult();
    return new PageImpl<>(content, pageable, total);
  }

  @Override
  public List<Batch> findAvailabilityBatchesForProducts(
      UUID tenantId, Filter filter, Collection<UUID> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return List.of();
    }
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Batch> query = cb.createQuery(Batch.class);
    Root<Batch> batch = query.from(Batch.class);
    query
        .select(batch)
        .where(predicates(cb, query, batch, tenantId, filter, productIds))
        .orderBy(
            cb.asc(batch.get("productId")),
            cb.asc(batch.get("batchCode")),
            cb.asc(batch.get("id")));
    return entityManager.createQuery(query).getResultList();
  }

  private Predicate[] predicates(
      CriteriaBuilder cb,
      CriteriaQuery<?> query,
      Root<Batch> batch,
      UUID tenantId,
      Filter filter,
      Collection<UUID> productIds) {
    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(batch.get("tenantId"), tenantId));
    predicates.add(cb.isTrue(batch.get("isActive")));
    predicates.add(batch.get("status").in(SALEABLE_BATCH_STATUSES));
    predicates.add(batch.get("productType").in(AVAILABILITY_PRODUCT_TYPES));
    if (filter.colorId() != null) {
      predicates.add(cb.equal(batch.get("colorId"), filter.colorId()));
    } else if (filter.colourless()) {
      predicates.add(cb.isNull(batch.get("colorId")));
    }
    if (filter.productId() != null) {
      predicates.add(cb.equal(batch.get("productId"), filter.productId()));
    }
    if (filter.batchId() != null) {
      predicates.add(cb.equal(batch.get("id"), filter.batchId()));
    }
    if (productIds != null) {
      predicates.add(batch.get("productId").in(productIds));
    }
    if (filter.qualityGradeId() != null || filter.qualityUnassigned()) {
      Subquery<Integer> pieceExists = query.subquery(Integer.class);
      Root<StockUnit> piece = pieceExists.from(StockUnit.class);
      List<Predicate> piecePredicates = new ArrayList<>();
      piecePredicates.add(cb.equal(piece.get("tenantId"), tenantId));
      piecePredicates.add(cb.equal(piece.get("batchId"), batch.get("id")));
      piecePredicates.add(cb.isTrue(piece.get("isActive")));
      piecePredicates.add(piece.get("status").in(SELECTABLE_PIECE_STATUSES));
      if (filter.qualityGradeId() != null) {
        piecePredicates.add(cb.equal(piece.get("qualityGradeId"), filter.qualityGradeId()));
      } else {
        piecePredicates.add(cb.isNull(piece.get("qualityGradeId")));
      }
      pieceExists.select(cb.literal(1)).where(piecePredicates.toArray(Predicate[]::new));
      predicates.add(cb.exists(pieceExists));
    }
    return predicates.toArray(Predicate[]::new);
  }

  private <T> TypedQuery<T> page(TypedQuery<T> query, Pageable pageable) {
    return query
        .setFirstResult(Math.toIntExact(pageable.getOffset()))
        .setMaxResults(pageable.getPageSize());
  }
}
