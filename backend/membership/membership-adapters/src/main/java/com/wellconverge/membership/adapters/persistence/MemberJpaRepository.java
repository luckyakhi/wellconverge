package com.wellconverge.membership.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository over {@link MemberEntity}. */
public interface MemberJpaRepository extends JpaRepository<MemberEntity, UUID> {

    Optional<MemberEntity> findByEmail(String email);
}
