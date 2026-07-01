package com.fabricmanagement.platform.auth.infra.repository;

import com.fabricmanagement.platform.auth.domain.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

  List<Membership> findByLoginIdentityId(UUID loginIdentityId);

  Optional<Membership> findByUserId(UUID userId);
}
