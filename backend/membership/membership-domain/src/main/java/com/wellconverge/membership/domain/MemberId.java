package com.wellconverge.membership.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed identity for a {@link Member}. We never pass bare UUIDs/strings around the domain. */
public record MemberId(UUID value) {

    public MemberId {
        Objects.requireNonNull(value, "MemberId value must not be null");
    }

    public static MemberId newId() {
        return new MemberId(UUID.randomUUID());
    }

    public static MemberId of(UUID value) {
        return new MemberId(value);
    }

    public static MemberId of(String value) {
        return new MemberId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
