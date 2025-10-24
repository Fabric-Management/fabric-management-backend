package com.fabricmanagement.common.infrastructure.cqrs;

/**
 * Marker interface for all commands in the system.
 *
 * <p>Commands represent write operations (Create, Update, Delete) that
 * change the state of the system. They follow the Command pattern and
 * CQRS (Command Query Responsibility Segregation) principle.</p>
 *
 * <h2>Characteristics:</h2>
 * <ul>
 *   <li>Represents an intention to change state</li>
 *   <li>Has a clear name in imperative form (CreateMaterial, UpdateUser)</li>
 *   <li>Contains all data needed to execute the operation</li>
 *   <li>Should be immutable (use @Value or final fields)</li>
 *   <li>Validated before execution</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Value
 * public class CreateMaterialCommand implements Command {
 *     UUID tenantId;
 *     String name;
 *     MaterialType type;
 *     BigDecimal unitCost;
 * }
 *
 * @Service
 * @RequiredArgsConstructor
 * public class MaterialCommandHandler implements CommandHandler<CreateMaterialCommand> {
 *     private final MaterialRepository repository;
 *
 *     @Override
 *     public void handle(CreateMaterialCommand command) {
 *         Material material = Material.create(command);
 *         repository.save(material);
 *     }
 * }
 * }</pre>
 *
 * @see CommandHandler
 * @see Query
 */
public interface Command {
}

