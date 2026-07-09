package com.wellconverge.membership.domain.event;

import com.wellconverge.membership.domain.EmailAddress;
import com.wellconverge.membership.domain.MemberId;

import java.time.Instant;

/** Raised when a new member is registered. */
public record MemberRegistered(MemberId memberId, EmailAddress email, Instant occurredAt)
        implements DomainEvent {
}
