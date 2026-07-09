package com.wellconverge.membership.application.port.out;

import com.wellconverge.membership.domain.event.DomainEvent;

import java.util.List;

/**
 * Outbound port for emitting domain events. Iteration 1 uses a simple logging implementation;
 * a later iteration replaces it with a real in-process/async event bus.
 */
public interface DomainEventPublisher {

    void publish(List<DomainEvent> events);
}
