package com.fabricmanagement.common.infrastructure.cqrs;

/**
 * Marker interface for all commands in the system.
 *
 * <p>Commands represent write operations (Create, Update, Delete) that change the state of the
 * system. They follow the Command pattern and CQRS (Command Query Responsibility Segregation)
 * principle.
 *
 * <h2>Characteristics:</h2>
 *
 * <ul>
 *   <li>Represents an intention to change state
 *   <li>Has a clear name in imperative form (CreateProduct, UpdateUser)
 *   <li>Contains all data needed to execute the operation
 *   <li>Should be immutable (use @Value or final fields)
 *   <li>Validated before execution
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @Value
 * public class CreateProductCommand implements Command {
 *     UUID tenantId;
 *     String name;
 *     ProductType type;
 *     BigDecimal unitCost;
 * }
 *
 * @Service
 * @RequiredArgsConstructor
 * public class ProductCommandHandler implements CommandHandler<CreateProductCommand> {
 *     private final ProductRepository repository;
 *
 *     @Override
 *     public void handle(CreateProductCommand command) {
 *         Product product = Product.create(command);
 *         repository.save(product);
 *     }
 * }
 * }</pre>
 *
 * @see CommandHandler
 * @see Query
 */
public interface Command {}
