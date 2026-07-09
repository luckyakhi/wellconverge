package com.wellconverge.membership.application.service;

import com.wellconverge.membership.application.EmailAlreadyRegisteredException;
import com.wellconverge.membership.application.port.in.RegisterMemberCommand;
import com.wellconverge.membership.application.port.in.RegisterMemberUseCase;
import com.wellconverge.membership.application.port.out.DomainEventPublisher;
import com.wellconverge.membership.application.port.out.MemberRepository;
import com.wellconverge.membership.domain.EmailAddress;
import com.wellconverge.membership.domain.FullName;
import com.wellconverge.membership.domain.Member;
import com.wellconverge.membership.domain.MemberId;

import java.time.Clock;
import java.util.Objects;

public class RegisterMemberService implements RegisterMemberUseCase {

    private final MemberRepository members;
    private final DomainEventPublisher events;
    private final Clock clock;

    public RegisterMemberService(MemberRepository members, DomainEventPublisher events, Clock clock) {
        this.members = Objects.requireNonNull(members);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public MemberId register(RegisterMemberCommand command) {
        EmailAddress email = EmailAddress.of(command.email());   // INV-1 validation
        FullName fullName = FullName.of(command.fullName());     // INV-1 validation

        members.findByEmail(email).ifPresent(existing -> {
            throw new EmailAlreadyRegisteredException(email);    // INV-2
        });

        Member member = Member.register(email, fullName, clock.instant());
        members.save(member);
        events.publish(member.pullDomainEvents());
        return member.id();
    }
}
