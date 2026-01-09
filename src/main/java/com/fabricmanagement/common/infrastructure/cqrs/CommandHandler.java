package com.fabricmanagement.common.infrastructure.cqrs;

/**
 * Handler for processing commands.
 *
 * <p>Command handlers contain the business logic for executing commands. They are responsible for:
 *
 * <ul>
 *   <li>Validating command data
 *   <li>Executing business logic
 *   <li>Persisting state changes
 *   <li>Publishing domain events
 * </ul>
 *
 * <h2>Best Practices:</h2>
 *
 * <ul>
 *   <li>One handler per command (Single Responsibility)
 *   <li>Transactional by default (@Transactional)
 *   <li>Publish events after state change
 *   <li>Return void or simple confirmation
 * </ul>
 *
 * @param <C> the command type
 * @see Command
 */
@FunctionalInterface
public interface CommandHandler<C extends Command> {

  /**
   * Handles the given command.
   *
   * @param command the command to handle
   * @throws IllegalArgumentException if command validation fails
   * @throws RuntimeException if command execution fails
   */
  void handle(C command);
}
