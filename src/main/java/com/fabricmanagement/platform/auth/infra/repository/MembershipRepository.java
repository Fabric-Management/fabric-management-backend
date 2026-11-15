package com.fabricmanagement.platform.auth.infra.repository;

import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

  List<Membership> findByLoginIdentityId(UUID loginIdentityId);

  List<Membership> findByLoginIdentityIdAndStatus(UUID loginIdentityId, MembershipStatus status);

  Optional<Membership> findByUserId(UUID userId);

  Optional<Membership> findByLoginIdentityIdAndTenantId(UUID loginIdentityId, UUID tenantId);

  long countByLoginIdentityId(UUID loginIdentityId);

  long countByLoginIdentityIdAndStatus(UUID loginIdentityId, MembershipStatus status);

  /** The login e-mail of each given user that has a login identity (a user has one at most). */
  @Query(
      "select m.userId as userId, li.email as email from Membership m join m.loginIdentity li"
          + " where m.userId in :userIds")
  List<UserLoginEmail> findLoginEmailsByUserIds(@Param("userIds") Collection<UUID> userIds);

  /** A user's login e-mail. */
  interface UserLoginEmail {
    UUID getUserId();

    String getEmail();
  }
}
