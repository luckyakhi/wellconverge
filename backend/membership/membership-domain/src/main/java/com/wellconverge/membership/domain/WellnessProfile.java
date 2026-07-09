package com.wellconverge.membership.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The profile a member declares during onboarding: at least one wellness goal (INV-3) and an optional
 * date of birth that must be in the past (INV-5).
 */
public record WellnessProfile(Set<WellnessGoal> goals, LocalDate dateOfBirth) {

    public WellnessProfile {
        if (goals == null || goals.isEmpty()) {
            throw new DomainException("Onboarding requires at least one wellness goal");
        }
        // Defensive copy into an immutable, order-stable set.
        goals = EnumSet.copyOf(goals);
        if (dateOfBirth != null && !dateOfBirth.isBefore(LocalDate.now())) {
            throw new DomainException("Date of birth must be in the past");
        }
    }

    public static WellnessProfile of(Set<WellnessGoal> goals, LocalDate dateOfBirth) {
        return new WellnessProfile(goals, dateOfBirth);
    }

    public Optional<LocalDate> dateOfBirthOptional() {
        return Optional.ofNullable(dateOfBirth);
    }
}
