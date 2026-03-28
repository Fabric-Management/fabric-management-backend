package com.fabricmanagement.human.payroll.app;

import com.fabricmanagement.human.core.employee.app.EmployeeService;
import com.fabricmanagement.human.payroll.dto.SalarySlipDto;
import com.fabricmanagement.human.payroll.infra.repository.PayRunPayoutRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollSelfServiceAppService {

  private final EmployeeService employeeService;
  private final PayRunPayoutRepository payRunPayoutRepository;

  @Transactional(readOnly = true)
  public List<SalarySlipDto> getSalarySlipsForUser(UUID userId) {
    return employeeService
        .getEmployeeByUserId(userId)
        .map(
            employee ->
                payRunPayoutRepository.findDescByEmployeeId(employee.getId()).stream()
                    .map(SalarySlipDto::from)
                    .collect(Collectors.toList()))
        .orElse(List.of());
  }
}
