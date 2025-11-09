package com.fabricmanagement.human.leave.infra.repository;

import com.fabricmanagement.human.leave.domain.HolidayCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, UUID> {

    @Query("""
        select hc from HolidayCalendar hc
        where hc.tenantId = :tenantId
          and hc.countryCode = :countryCode
          and hc.calendarYear = :year
        """)
    Optional<HolidayCalendar> findByCountryAndYear(@Param("tenantId") UUID tenantId,
                                                   @Param("countryCode") String countryCode,
                                                   @Param("year") Integer year);
}

