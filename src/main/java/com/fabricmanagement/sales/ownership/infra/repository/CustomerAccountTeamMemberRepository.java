package com.fabricmanagement.sales.ownership.infra.repository;

import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMember;
import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerAccountTeamMemberRepository
    extends JpaRepository<CustomerAccountTeamMember, CustomerAccountTeamMemberId> {

  Optional<CustomerAccountTeamMember> findByTenantIdAndCustomerIdAndUserId(
      UUID tenantId, UUID customerId, UUID userId);

  List<CustomerAccountTeamMember> findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
      UUID tenantId, UUID customerId);
}
