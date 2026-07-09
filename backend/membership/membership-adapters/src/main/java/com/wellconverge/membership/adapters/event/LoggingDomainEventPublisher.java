package com.wellconverge.membership.adapters.event;

import com.wellconverge.membership.application.port.out.DomainEventPublisher;
import com.wellconverge.membership.domain.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Iteration-1 event publisher: logs events. A later iteration swaps this for a real event bus
 * so other bounded contexts can react (e.g. Notifications on MemberOnboarded).
 */
@Component
class LoggingDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisher.class);

    @Override
    public void publish(List<DomainEvent> events) {
        events.forEach(event -> log.info("Domain event: {}", event));
    }
}
