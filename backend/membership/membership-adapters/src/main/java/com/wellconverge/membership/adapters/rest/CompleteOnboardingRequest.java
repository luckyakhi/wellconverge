package com.wellconverge.membership.adapters.rest;

import com.wellconverge.membership.domain.WellnessGoal;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.Set;

/** HTTP body for POST /api/members/{id}/onboarding. */
public record CompleteOnboardingRequest(
        @NotEmpty Set<WellnessGoal> goals,
        LocalDate dateOfBirth) {
}
