package com.wellconverge.membership.adapters.rest;

import jakarta.validation.constraints.NotBlank;

/** HTTP body for POST /api/members. Shape validation only; domain rules live in the domain. */
public record RegisterMemberRequest(
        @NotBlank String email,
        @NotBlank String fullName) {
}
