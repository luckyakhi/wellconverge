package com.wellconverge.membership.domain;

/** Thrown when onboarding is attempted on a member that is already onboarded (INV-4). */
public class AlreadyOnboardedException extends DomainException {
    public AlreadyOnboardedException(MemberId memberId) {
        super("Member %s has already completed onboarding".formatted(memberId));
    }
}
