package com.fabricmanagement.human.payroll.infra.repository;

import com.fabricmanagement.human.payroll.domain.PayRun;
import com.fabricmanagement.human.payroll.domain.PayRunEntry;
import com.fabricmanagement.human.payroll.domain.PayRunEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PayRunEntryRepository extends JpaRepository<PayRunEntry, UUID> {

    @Query("""
        select e from PayRunEntry e
        where e.payRun = :payRun
        """)
    List<PayRunEntry> findByPayRun(@Param("payRun") PayRun payRun);

    @Query("""
        select e from PayRunEntry e
        where e.payRun = :payRun
          and e.entryType = :entryType
        """)
    List<PayRunEntry> findByType(@Param("payRun") PayRun payRun,
                                 @Param("entryType") PayRunEntryType entryType);
}

