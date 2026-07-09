package com.wellconverge.membership.domain.event;

import com.wellconverge.membership.domain.MemberId;
import com.wellconverge.membership.domain.WellnessGoal;

import java.time.Instant;
import java.util.Set;

/** Raised when a member completes onboarding. */
public record MemberOnboarded(MemberId memberId, Set<WellnessGoal> goals, Instant occurredAt)
        implements DomainEvent {
}
