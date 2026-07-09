package com.wellconverge.membership.application.port.in;

import com.wellconverge.membership.domain.WellnessGoal;

import java.time.LocalDate;
import java.util.Set;

/** Intent to complete onboarding for an existing member. */
public record CompleteOnboardingCommand(String memberId, Set<WellnessGoal> goals, LocalDate dateOfBirth) {
}
