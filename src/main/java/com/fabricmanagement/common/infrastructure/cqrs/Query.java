package com.fabricmanagement.common.infrastructure.cqrs;

/**
 * Marker interface for all queries in the system.
 *
 * <p>Queries represent read operations that return data without modifying state.
 * They follow the CQRS (Command Query Responsibility Segregation) principle.</p>
 *
 * <h2>Characteristics:</h2>
 * <ul>
 *   <li>Represents a request for data</li>
 *   <li>Has a clear name (GetMaterialQuery, SearchUsersQuery)</li>
 *   <li>Contains criteria/filters for data retrieval</li>
 *   <li>Should be immutable (use @Value or final fields)</li>
 *   <li>Does NOT modify system state</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Value
 * public class GetMaterialQuery implements Query<MaterialDto> {
 *     UUID tenantId;
 *     UUID materialId;
 * }
 *
 * @Service
 * @RequiredArgsConstructor
 * public class MaterialQueryHandler implements QueryHandler<GetMaterialQuery, MaterialDto> {
 *     private final MaterialRepository repository;
 *
 *     @Override
 *     public MaterialDto handle(GetMaterialQuery query) {
 *         return repository.findByTenantIdAndId(query.getTenantId(), query.getMaterialId())
 *             .map(MaterialDto::from)
 *             .orElseThrow(() -> new EntityNotFoundException("Material not found"));
 *     }
 * }
 * }</pre>
 *
 * @param <R> the result type returned by this query
 * @see QueryHandler
 * @see Command
 */
public interface Query<R> {
}

