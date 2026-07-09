package com.wellconverge.membership.adapters.rest;

import com.wellconverge.membership.application.port.in.MemberView;
import com.wellconverge.membership.domain.WellnessGoal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/** HTTP representation of a member. */
public record MemberResponse(
        String id,
        String email,
        String fullName,
        String status,
        Set<WellnessGoal> goals,
        LocalDate dateOfBirth,
        Instant registeredAt,
        Instant onboardedAt) {

    public static MemberResponse from(MemberView view) {
        return new MemberResponse(
                view.id(),
                view.email(),
                view.fullName(),
                view.status(),
                view.goals(),
                view.dateOfBirth(),
                view.registeredAt(),
                view.onboardedAt());
    }
}
