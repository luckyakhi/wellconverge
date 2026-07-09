package com.wellconverge.membership.application.service;

import com.wellconverge.membership.application.MemberNotFoundException;
import com.wellconverge.membership.application.port.in.GetMemberUseCase;
import com.wellconverge.membership.application.port.in.MemberView;
import com.wellconverge.membership.application.port.out.MemberRepository;
import com.wellconverge.membership.domain.MemberId;

import java.util.Objects;

public class GetMemberService implements GetMemberUseCase {

    private final MemberRepository members;

    public GetMemberService(MemberRepository members) {
        this.members = Objects.requireNonNull(members);
    }

    @Override
    public MemberView getById(MemberId memberId) {
        return members.findById(memberId)
                .map(MemberView::from)
                .orElseThrow(() -> new MemberNotFoundException(memberId.toString()));
    }
}
