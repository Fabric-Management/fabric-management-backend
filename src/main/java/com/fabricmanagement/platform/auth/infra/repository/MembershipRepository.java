package com.fabricmanagement.platform.auth.infra.repository;

import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

  List<Membership> findByLoginIdentityId(UUID loginIdentityId);

  List<Membership> findByLoginIdentityIdAndStatus(UUID loginIdentityId, MembershipStatus status);

  Optional<Membership> findByUserId(UUID userId);

  Optional<Membership> findByLoginIdentityIdAndTenantId(UUID loginIdentityId, UUID tenantId);

  long countByLoginIdentityId(UUID loginIdentityId);
}
