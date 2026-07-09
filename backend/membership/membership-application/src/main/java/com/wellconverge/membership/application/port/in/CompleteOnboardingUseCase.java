package com.wellconverge.membership.application.port.in;

/** Inbound port: complete a member's onboarding. */
public interface CompleteOnboardingUseCase {

    /**
     * @return a view of the member after onboarding
     * @throws com.wellconverge.membership.application.MemberNotFoundException          if no such member
     * @throws com.wellconverge.membership.domain.AlreadyOnboardedException             if already onboarded (INV-4)
     * @throws com.wellconverge.membership.domain.DomainException                       if the profile is invalid (INV-3, INV-5)
     */
    MemberView completeOnboarding(CompleteOnboardingCommand command);
}
