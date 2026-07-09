package com.wellconverge.membership.domain;

/** A member's full name (INV-1). Trimmed, non-blank, bounded length. */
public record FullName(String value) {

    private static final int MAX_LENGTH = 120;

    public FullName {
        if (value == null || value.isBlank()) {
            throw new DomainException("Full name must not be blank");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH) {
            throw new DomainException("Full name must be at most %d characters".formatted(MAX_LENGTH));
        }
    }

    public static FullName of(String value) {
        return new FullName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
