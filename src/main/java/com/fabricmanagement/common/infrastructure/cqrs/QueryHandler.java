package com.fabricmanagement.common.infrastructure.cqrs;

/**
 * Handler for processing queries.
 *
 * <p>Query handlers are responsible for retrieving data from the system.
 * They should:
 * <ul>
 *   <li>Read data from repositories</li>
 *   <li>Transform entities to DTOs</li>
 *   <li>Apply filters and pagination</li>
 *   <li>NOT modify system state</li>
 * </ul>
 *
 * <h2>Best Practices:</h2>
 * <ul>
 *   <li>One handler per query (Single Responsibility)</li>
 *   <li>Read-only operations (@Transactional(readOnly = true))</li>
 *   <li>Cache results when appropriate (@Cacheable)</li>
 *   <li>Return DTOs, not entities</li>
 * </ul>
 *
 * @param <Q> the query type
 * @param <R> the result type
 * @see Query
 */
@FunctionalInterface
public interface QueryHandler<Q extends Query<R>, R> {

    /**
     * Handles the given query and returns the result.
     *
     * @param query the query to handle
     * @return the query result
     * @throws IllegalArgumentException if query validation fails
     * @throws RuntimeException if query execution fails
     */
    R handle(Q query);
}

