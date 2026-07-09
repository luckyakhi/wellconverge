package com.wellconverge.config;

import com.wellconverge.membership.application.port.in.CompleteOnboardingUseCase;
import com.wellconverge.membership.application.port.in.GetMemberUseCase;
import com.wellconverge.membership.application.port.in.RegisterMemberUseCase;
import com.wellconverge.membership.application.port.out.DomainEventPublisher;
import com.wellconverge.membership.application.port.out.MemberRepository;
import com.wellconverge.membership.application.service.CompleteOnboardingService;
import com.wellconverge.membership.application.service.GetMemberService;
import com.wellconverge.membership.application.service.RegisterMemberService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the framework-free application services into Spring beans. The services themselves know
 * nothing about Spring; this composition root supplies their ports.
 */
@Configuration
public class MembershipConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RegisterMemberUseCase registerMemberUseCase(MemberRepository members,
                                                DomainEventPublisher events,
                                                Clock clock) {
        return new RegisterMemberService(members, events, clock);
    }

    @Bean
    CompleteOnboardingUseCase completeOnboardingUseCase(MemberRepository members,
                                                        DomainEventPublisher events,
                                                        Clock clock) {
        return new CompleteOnboardingService(members, events, clock);
    }

    @Bean
    GetMemberUseCase getMemberUseCase(MemberRepository members) {
        return new GetMemberService(members);
    }
}
