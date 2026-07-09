package com.wellconverge.membership.application.port.in;

import com.wellconverge.membership.domain.Member;
import com.wellconverge.membership.domain.WellnessGoal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/** Read model returned to callers — a stable projection of a {@link Member}. */
public record MemberView(
        String id,
        String email,
        String fullName,
        String status,
        Set<WellnessGoal> goals,
        LocalDate dateOfBirth,
        Instant registeredAt,
        Instant onboardedAt) {

    public static MemberView from(Member member) {
        Set<WellnessGoal> goals = member.profile() == null ? Set.of() : member.profile().goals();
        LocalDate dob = member.profile() == null ? null : member.profile().dateOfBirth();
        return new MemberView(
                member.id().toString(),
                member.email().value(),
                member.fullName().value(),
                member.status().name(),
                goals,
                dob,
                member.registeredAt(),
                member.onboardedAt());
    }
}
