package com.fabricmanagement.human.leave.app;

import com.fabricmanagement.human.leave.domain.HolidayCalendar;
import com.fabricmanagement.human.leave.infra.repository.HolidayCalendarRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HolidayCalendarService {

  private final HolidayCalendarRepository holidayCalendarRepository;
  private final Clock clock;

  public Optional<HolidayCalendar> findForYear(UUID tenantId, String countryCode, Integer year) {
    if (countryCode == null || countryCode.isBlank()) {
      return Optional.empty();
    }
    int resolvedYear = year != null ? year : LocalDate.now(clock).getYear();
    return holidayCalendarRepository.findByCountryAndYear(
        tenantId, countryCode.toUpperCase(), resolvedYear);
  }
}
