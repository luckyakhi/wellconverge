package com.wellconverge.membership.domain;

import com.wellconverge.membership.domain.event.DomainEvent;
import com.wellconverge.membership.domain.event.MemberOnboarded;
import com.wellconverge.membership.domain.event.MemberRegistered;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:00:00Z");

    @Test
    void registeringAMemberStartsInRegisteredStatusAndRaisesEvent() {
        Member member = Member.register(EmailAddress.of("ada@example.com"), FullName.of("Ada Lovelace"), NOW);

        assertThat(member.status()).isEqualTo(MemberStatus.REGISTERED);
        assertThat(member.registeredAt()).isEqualTo(NOW);
        assertThat(member.pullDomainEvents()).singleElement().isInstanceOf(MemberRegistered.class);
    }

    @Test
    void completingOnboardingTransitionsToOnboardedAndRaisesEvent() {
        Member member = Member.register(EmailAddress.of("ada@example.com"), FullName.of("Ada Lovelace"), NOW);
        member.pullDomainEvents(); // drain registration event

        WellnessProfile profile = WellnessProfile.of(EnumSet.of(WellnessGoal.SLEEP_BETTER), null);
        member.completeOnboarding(profile, NOW);

        assertThat(member.status()).isEqualTo(MemberStatus.ONBOARDED);
        List<DomainEvent> events = member.pullDomainEvents();
        assertThat(events).singleElement().isInstanceOf(MemberOnboarded.class);
    }

    @Test
    void onboardingTwiceIsRejected() {
        Member member = Member.register(EmailAddress.of("ada@example.com"), FullName.of("Ada Lovelace"), NOW);
        WellnessProfile profile = WellnessProfile.of(EnumSet.of(WellnessGoal.MOVE_MORE), null);
        member.completeOnboarding(profile, NOW);

        assertThatThrownBy(() -> member.completeOnboarding(profile, NOW))
                .isInstanceOf(AlreadyOnboardedException.class);
    }
}
