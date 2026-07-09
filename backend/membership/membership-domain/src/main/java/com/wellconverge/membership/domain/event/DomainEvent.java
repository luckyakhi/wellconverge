package com.wellconverge.membership.domain.event;

import java.time.Instant;

/** Something significant that happened in the domain. Sealed to the Membership events we publish. */
public sealed interface DomainEvent permits MemberRegistered, MemberOnboarded {
    Instant occurredAt();
}
