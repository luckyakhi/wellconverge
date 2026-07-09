package com.wellconverge.membership.application.port.in;

/** Intent to register a new member. Raw input; the domain validates the values. */
public record RegisterMemberCommand(String email, String fullName) {
}
