package com.wellconverge.membership.application;

import com.wellconverge.membership.domain.DomainException;
import com.wellconverge.membership.domain.EmailAddress;

/** Enforces INV-2 (unique email) at the application boundary, where the whole member set is visible. */
public class EmailAlreadyRegisteredException extends DomainException {
    public EmailAlreadyRegisteredException(EmailAddress email) {
        super("Email %s is already registered".formatted(email));
    }
}
