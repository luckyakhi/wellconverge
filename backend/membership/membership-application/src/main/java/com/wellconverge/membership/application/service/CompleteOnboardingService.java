package com.wellconverge.membership.application.service;

import com.wellconverge.membership.application.MemberNotFoundException;
import com.wellconverge.membership.application.port.in.CompleteOnboardingCommand;
import com.wellconverge.membership.application.port.in.CompleteOnboardingUseCase;
import com.wellconverge.membership.application.port.in.MemberView;
import com.wellconverge.membership.application.port.out.DomainEventPublisher;
import com.wellconverge.membership.application.port.out.MemberRepository;
import com.wellconverge.membership.domain.Member;
import com.wellconverge.membership.domain.MemberId;
import com.wellconverge.membership.domain.WellnessProfile;

import java.time.Clock;
import java.util.Objects;

public class CompleteOnboardingService implements CompleteOnboardingUseCase {

    private final MemberRepository members;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CompleteOnboardingService(MemberRepository members, DomainEventPublisher events, Clock clock) {
        this.members = Objects.requireNonNull(members);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public MemberView completeOnboarding(CompleteOnboardingCommand command) {
        MemberId id = MemberId.of(command.memberId());
        Member member = members.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(command.memberId()));

        // Domain validates INV-3 (>=1 goal) and INV-5 (dob in past); aggregate enforces INV-4.
        WellnessProfile profile = WellnessProfile.of(command.goals(), command.dateOfBirth());
        member.completeOnboarding(profile, clock.instant());

        members.save(member);
        events.publish(member.pullDomainEvents());
        return MemberView.from(member);
    }
}
