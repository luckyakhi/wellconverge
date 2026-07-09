package com.wellconverge.membership.adapters.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** JPA persistence model for a member. Deliberately separate from the {@link com.wellconverge.membership.domain.Member} aggregate. */
@Entity
@Table(name = "member")
class MemberEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "status", nullable = false)
    private String status;

    /** Comma-separated goal names; null until onboarded. */
    @Column(name = "goals")
    private String goals;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    protected MemberEntity() {
        // for JPA
    }

    MemberEntity(UUID id, String email, String fullName, String status, String goals,
                 LocalDate dateOfBirth, Instant registeredAt, Instant onboardedAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.status = status;
        this.goals = goals;
        this.dateOfBirth = dateOfBirth;
        this.registeredAt = registeredAt;
        this.onboardedAt = onboardedAt;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getFullName() {
        return fullName;
    }

    String getStatus() {
        return status;
    }

    String getGoals() {
        return goals;
    }

    LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    Instant getRegisteredAt() {
        return registeredAt;
    }

    Instant getOnboardedAt() {
        return onboardedAt;
    }
}
