package com.wellconverge.membership.domain;

import java.util.regex.Pattern;

/**
 * A member's email. Normalized to trimmed lowercase and validated against a basic shape (INV-1).
 * Uniqueness across members (INV-2) is enforced at the repository boundary, not here.
 */
public record EmailAddress(String value) {

    // Deliberately simple; full RFC 5322 validation is out of scope for a learning slice.
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new DomainException("Email must not be blank");
        }
        value = value.trim().toLowerCase();
        if (!SHAPE.matcher(value).matches()) {
            throw new DomainException("Email '%s' is not a valid email address".formatted(value));
        }
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
