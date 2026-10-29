/**
 * The events a sales order publishes for other modules. This is the only part of the sales order
 * that finance's collection module may depend on: it reacts to these events and reads order data
 * through its own {@code CollectionSourcePort}, never through sales classes.
 *
 * <p>Keep event class names and packages stable: they are stored in the durable event registry and
 * replayed after a deployment.
 */
@NamedInterface("events")
package com.fabricmanagement.sales.salesorder.domain.event;

import org.springframework.modulith.NamedInterface;
