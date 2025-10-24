package com.fabricmanagement.common.infrastructure.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publisher for domain events.
 *
 * <p>Wraps Spring's ApplicationEventPublisher with domain-specific logging
 * and eventual consistency support via Spring Modulith event publication.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class MaterialService {
 *     private final DomainEventPublisher eventPublisher;
 *
 *     public void createMaterial(CreateMaterialRequest request) {
 *         Material material = materialRepository.save(Material.create(request));
 *         
 *         eventPublisher.publish(new MaterialCreatedEvent(
 *             TenantContext.getCurrentTenantId(),
 *             material.getId(),
 *             material.getName()
 *         ));
 *     }
 * }
 * }</pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publishes a domain event to all interested listeners.
     *
     * @param event the domain event to publish
     */
    public void publish(DomainEvent event) {
        log.debug("Publishing domain event: {}", event);
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * Publishes multiple domain events.
     *
     * @param events the domain events to publish
     */
    public void publishAll(DomainEvent... events) {
        for (DomainEvent event : events) {
            publish(event);
        }
    }
}

