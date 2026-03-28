package com.fabricmanagement.platform.user.api.facade;

import com.fabricmanagement.platform.user.app.UserDepartmentService;
import com.fabricmanagement.platform.user.dto.UserDepartmentDto;
import com.fabricmanagement.platform.user.mapper.UserDepartmentMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDepartmentFacade {

  private final UserDepartmentService userDepartmentService;
  private final UserDepartmentMapper userDepartmentMapper;

  @Transactional(readOnly = true)
  public List<UserDepartmentDto> getUserDepartments(UUID userId) {
    return userDepartmentMapper.toDtoList(userDepartmentService.getUserDepartments(userId));
  }

  @Transactional(readOnly = true)
  public Optional<UserDepartmentDto> getPrimaryDepartment(UUID userId) {
    return userDepartmentService.getPrimaryDepartment(userId).map(userDepartmentMapper::toDto);
  }

  @Transactional
  public UserDepartmentDto assignDepartment(
      UUID userId, UUID departmentId, Boolean isPrimary, UUID assignedBy) {
    return userDepartmentMapper.toDto(
        userDepartmentService.assignDepartment(userId, departmentId, isPrimary, assignedBy));
  }

  @Transactional
  public void setPrimaryDepartment(UUID userId, UUID departmentId) {
    userDepartmentService.setPrimaryDepartment(userId, departmentId);
  }

  @Transactional
  public void removeAssignment(UUID userId, UUID departmentId) {
    userDepartmentService.removeAssignment(userId, departmentId);
  }
}
