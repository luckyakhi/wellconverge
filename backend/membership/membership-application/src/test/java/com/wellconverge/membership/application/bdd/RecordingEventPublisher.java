package com.wellconverge.membership.application.bdd;

import com.wellconverge.membership.application.port.out.DomainEventPublisher;
import com.wellconverge.membership.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/** Captures published events so scenarios can assert on them. */
class RecordingEventPublisher implements DomainEventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(List<DomainEvent> events) {
        published.addAll(events);
    }

    List<DomainEvent> published() {
        return published;
    }
}
