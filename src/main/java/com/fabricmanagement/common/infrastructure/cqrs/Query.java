package com.fabricmanagement.common.infrastructure.cqrs;

/**
 * Marker interface for all queries in the system.
 *
 * <p>Queries represent read operations that return data without modifying state. They follow the
 * CQRS (Command Query Responsibility Segregation) principle.
 *
 * <h2>Characteristics:</h2>
 *
 * <ul>
 *   <li>Represents a request for data
 *   <li>Has a clear name (GetProductQuery, SearchUsersQuery)
 *   <li>Contains criteria/filters for data retrieval
 *   <li>Should be immutable (use @Value or final fields)
 *   <li>Does NOT modify system state
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @Value
 * public class GetProductQuery implements Query<ProductDto> {
 *     UUID tenantId;
 *     UUID productId;
 * }
 *
 * @Service
 * @RequiredArgsConstructor
 * public class ProductQueryHandler implements QueryHandler<GetProductQuery, ProductDto> {
 *     private final ProductRepository repository;
 *
 *     @Override
 *     public ProductDto handle(GetProductQuery query) {
 *         return repository.findByTenantIdAndId(query.getTenantId(), query.getProductId())
 *             .map(ProductDto::from)
 *             .orElseThrow(() -> new EntityNotFoundException("Product not found"));
 *     }
 * }
 * }</pre>
 *
 * @param <R> the result type returned by this query
 * @see QueryHandler
 * @see Command
 */
public interface Query<R> {}
