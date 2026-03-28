package com.fabricmanagement.platform.user.api.facade;

import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.dto.RoleDto;
import com.fabricmanagement.platform.user.mapper.RoleMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleFacade {

  private final RoleService roleService;
  private final RoleMapper roleMapper;

  @Transactional(readOnly = true)
  public List<RoleDto> findAll() {
    return roleMapper.toDtoList(roleService.findAll());
  }

  @Transactional(readOnly = true)
  public Optional<RoleDto> findById(UUID id) {
    return roleService.findById(id).map(roleMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Optional<RoleDto> findByCode(String code) {
    return roleService.findByCode(code).map(roleMapper::toDto);
  }

  @Transactional
  public RoleDto create(String roleName, String roleCode, String description) {
    return roleMapper.toDto(roleService.create(roleName, roleCode, description));
  }

  @Transactional
  public RoleDto update(UUID id, String roleName, String description) {
    return roleMapper.toDto(roleService.update(id, roleName, description));
  }

  @Transactional
  public void deactivate(UUID id) {
    roleService.deactivate(id);
  }
}
