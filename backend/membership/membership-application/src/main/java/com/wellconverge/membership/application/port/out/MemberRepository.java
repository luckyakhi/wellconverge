package com.wellconverge.membership.application.port.out;

import com.wellconverge.membership.domain.EmailAddress;
import com.wellconverge.membership.domain.Member;
import com.wellconverge.membership.domain.MemberId;

import java.util.Optional;

/** Outbound port for persisting and loading the {@link Member} aggregate. */
public interface MemberRepository {

    void save(Member member);

    Optional<Member> findById(MemberId id);

    Optional<Member> findByEmail(EmailAddress email);
}
