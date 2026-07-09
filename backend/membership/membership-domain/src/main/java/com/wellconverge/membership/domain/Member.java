package com.wellconverge.membership.domain;

import com.wellconverge.membership.domain.event.DomainEvent;
import com.wellconverge.membership.domain.event.MemberOnboarded;
import com.wellconverge.membership.domain.event.MemberRegistered;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root of the Membership context. Guards the member lifecycle invariants:
 * a member is always validly identified (INV-1), and can be onboarded at most once (INV-4).
 * Uniqueness of email across members (INV-2) is outside the aggregate — enforced by the repository.
 */
public class Member {

    private final MemberId id;
    private final EmailAddress email;
    private final FullName fullName;
    private MemberStatus status;
    private WellnessProfile profile; // null until onboarded
    private final Instant registeredAt;
    private Instant onboardedAt;      // null until onboarded

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private Member(MemberId id, EmailAddress email, FullName fullName, MemberStatus status,
                   WellnessProfile profile, Instant registeredAt, Instant onboardedAt) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.fullName = Objects.requireNonNull(fullName);
        this.status = Objects.requireNonNull(status);
        this.profile = profile;
        this.registeredAt = Objects.requireNonNull(registeredAt);
        this.onboardedAt = onboardedAt;
    }

    /** Factory: register a brand-new member. Raises {@link MemberRegistered}. */
    public static Member register(EmailAddress email, FullName fullName, Instant now) {
        MemberId id = MemberId.newId();
        Member member = new Member(id, email, fullName, MemberStatus.REGISTERED, null, now, null);
        member.pendingEvents.add(new MemberRegistered(id, email, now));
        return member;
    }

    /**
     * Reconstitute a member from persisted state. No events raised — this is not a new fact,
     * it is loading an existing one.
     */
    public static Member reconstitute(MemberId id, EmailAddress email, FullName fullName,
                                      MemberStatus status, WellnessProfile profile,
                                      Instant registeredAt, Instant onboardedAt) {
        return new Member(id, email, fullName, status, profile, registeredAt, onboardedAt);
    }

    /** Complete onboarding once. Enforces INV-4; raises {@link MemberOnboarded}. */
    public void completeOnboarding(WellnessProfile profile, Instant now) {
        if (status == MemberStatus.ONBOARDED) {
            throw new AlreadyOnboardedException(id);
        }
        this.profile = Objects.requireNonNull(profile, "profile");
        this.status = MemberStatus.ONBOARDED;
        this.onboardedAt = now;
        this.pendingEvents.add(new MemberOnboarded(id, profile.goals(), now));
    }

    /** Drain the events accumulated since the last pull (to be published by the application layer). */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> drained = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return drained;
    }

    public MemberId id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public FullName fullName() {
        return fullName;
    }

    public MemberStatus status() {
        return status;
    }

    public WellnessProfile profile() {
        return profile;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public Instant onboardedAt() {
        return onboardedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
