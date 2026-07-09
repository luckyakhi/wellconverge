package com.wellconverge.membership.application;

import com.wellconverge.membership.domain.DomainException;

/** Thrown when a member lookup fails. */
public class MemberNotFoundException extends DomainException {
    public MemberNotFoundException(String memberId) {
        super("Member %s was not found".formatted(memberId));
    }
}
