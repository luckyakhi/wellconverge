package com.wellconverge.membership.domain;

/**
 * Base type for violations of Membership business rules. Adapters map these to appropriate
 * transport errors (e.g. HTTP 400/409).
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
