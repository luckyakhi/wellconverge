package com.wellconverge.membership.application.bdd;

import com.wellconverge.membership.application.port.out.MemberRepository;
import com.wellconverge.membership.domain.EmailAddress;
import com.wellconverge.membership.domain.Member;
import com.wellconverge.membership.domain.MemberId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory outbound adapter so the BDD specs test behavior, not persistence plumbing. */
class InMemoryMemberRepository implements MemberRepository {

    private final Map<MemberId, Member> byId = new ConcurrentHashMap<>();

    @Override
    public void save(Member member) {
        byId.put(member.id(), member);
    }

    @Override
    public Optional<Member> findById(MemberId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Member> findByEmail(EmailAddress email) {
        return byId.values().stream()
                .filter(m -> m.email().equals(email))
                .findFirst();
    }
}
