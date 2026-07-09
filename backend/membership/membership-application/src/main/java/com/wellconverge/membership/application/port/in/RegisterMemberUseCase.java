package com.wellconverge.membership.application.port.in;

import com.wellconverge.membership.domain.MemberId;

/** Inbound port: register a new member. */
public interface RegisterMemberUseCase {

    /**
     * @return the id of the newly registered member
     * @throws com.wellconverge.membership.application.EmailAlreadyRegisteredException if the email is taken (INV-2)
     * @throws com.wellconverge.membership.domain.DomainException                       if email/name are invalid (INV-1)
     */
    MemberId register(RegisterMemberCommand command);
}
