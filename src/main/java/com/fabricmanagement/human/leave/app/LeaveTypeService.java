package com.fabricmanagement.human.leave.app;

import com.fabricmanagement.human.leave.domain.LeaveType;
import com.fabricmanagement.human.leave.infra.repository.LeaveTypeRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

  private final LeaveTypeRepository leaveTypeRepository;

  public LeaveType getActiveByCode(UUID tenantId, String leaveTypeCode) {
    return leaveTypeRepository
        .findActiveByCode(tenantId, normalize(leaveTypeCode))
        .orElseThrow(
            () ->
                new IllegalArgumentException("Leave type not found or inactive: " + leaveTypeCode));
  }

  public List<LeaveType> findActiveForCountry(UUID tenantId, String countryCode) {
    return leaveTypeRepository.findActiveForCountry(
        tenantId, countryCode != null ? countryCode.toUpperCase(Locale.ROOT) : null);
  }

  private String normalize(String code) {
    return code != null ? code.toUpperCase(Locale.ROOT) : null;
  }
}
