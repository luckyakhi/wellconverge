package com.wellconverge.membership.adapters.persistence;

import com.wellconverge.membership.application.port.out.MemberRepository;
import com.wellconverge.membership.domain.EmailAddress;
import com.wellconverge.membership.domain.FullName;
import com.wellconverge.membership.domain.Member;
import com.wellconverge.membership.domain.MemberId;
import com.wellconverge.membership.domain.MemberStatus;
import com.wellconverge.membership.domain.WellnessGoal;
import com.wellconverge.membership.domain.WellnessProfile;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Outbound adapter implementing the {@link MemberRepository} port over JPA. */
@Component
class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpa;

    MemberRepositoryAdapter(MemberJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Member member) {
        jpa.save(toEntity(member));
    }

    @Override
    public Optional<Member> findById(MemberId id) {
        return jpa.findById(id.value()).map(MemberRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Member> findByEmail(EmailAddress email) {
        return jpa.findByEmail(email.value()).map(MemberRepositoryAdapter::toDomain);
    }

    private static MemberEntity toEntity(Member member) {
        WellnessProfile profile = member.profile();
        String goals = profile == null ? null
                : profile.goals().stream().map(Enum::name).collect(Collectors.joining(","));
        return new MemberEntity(
                member.id().value(),
                member.email().value(),
                member.fullName().value(),
                member.status().name(),
                goals,
                profile == null ? null : profile.dateOfBirth(),
                member.registeredAt(),
                member.onboardedAt());
    }

    private static Member toDomain(MemberEntity entity) {
        WellnessProfile profile = null;
        if (entity.getGoals() != null && !entity.getGoals().isBlank()) {
            Set<WellnessGoal> goals = java.util.Arrays.stream(entity.getGoals().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(WellnessGoal::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(WellnessGoal.class)));
            profile = WellnessProfile.of(goals, entity.getDateOfBirth());
        }
        return Member.reconstitute(
                MemberId.of(entity.getId()),
                EmailAddress.of(entity.getEmail()),
                FullName.of(entity.getFullName()),
                MemberStatus.valueOf(entity.getStatus()),
                profile,
                entity.getRegisteredAt(),
                entity.getOnboardedAt());
    }
}
