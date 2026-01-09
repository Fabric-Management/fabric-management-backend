package com.fabricmanagement.human.leave.infra.repository;

import com.fabricmanagement.human.leave.domain.LeaveBalance;
import com.fabricmanagement.human.leave.domain.LeaveType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

  @Query(
      """
        select lb from LeaveBalance lb
        where lb.tenantId = :tenantId
          and lb.employeeId = :employeeId
          and lb.leaveType = :leaveType
        """)
  Optional<LeaveBalance> findByEmployeeAndType(
      @Param("tenantId") UUID tenantId,
      @Param("employeeId") UUID employeeId,
      @Param("leaveType") LeaveType leaveType);

  @Query(
      """
        select lb from LeaveBalance lb
        where lb.tenantId = :tenantId
          and lb.employeeId = :employeeId
        """)
  List<LeaveBalance> findByEmployee(
      @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
