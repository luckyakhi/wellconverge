package com.wellconverge.membership.application.port.in;

import com.wellconverge.membership.domain.MemberId;

/** Inbound port: fetch a member's current view. */
public interface GetMemberUseCase {

    /**
     * @throws com.wellconverge.membership.application.MemberNotFoundException if no such member
     */
    MemberView getById(MemberId memberId);
}
