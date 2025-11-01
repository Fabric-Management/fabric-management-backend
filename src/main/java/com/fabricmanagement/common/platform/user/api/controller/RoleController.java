package com.fabricmanagement.common.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.user.app.RoleService;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.dto.CreateRoleRequest;
import com.fabricmanagement.common.platform.user.dto.RoleDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleDto>>> getAllRoles() {
        log.debug("Getting all roles");

        List<RoleDto> roles = roleService.findAll()
            .stream()
            .map(RoleDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDto>> getRole(@PathVariable UUID id) {
        log.debug("Getting role: id={}", id);

        Role role = roleService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        return ResponseEntity.ok(ApiResponse.success(RoleDto.from(role)));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<RoleDto>> getRoleByCode(@PathVariable String code) {
        log.debug("Getting role by code: code={}", code);

        Role role = roleService.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        return ResponseEntity.ok(ApiResponse.success(RoleDto.from(role)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleDto>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        log.info("Creating role: roleName={}, roleCode={}", request.getRoleName(), request.getRoleCode());

        Role created = roleService.create(
            request.getRoleName(),
            request.getRoleCode(),
            request.getDescription()
        );

        return ResponseEntity.ok(ApiResponse.success(RoleDto.from(created), "Role created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDto>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRoleRequest request) {
        log.info("Updating role: id={}", id);

        Role updated = roleService.update(id, request.getRoleName(), request.getDescription());

        return ResponseEntity.ok(ApiResponse.success(RoleDto.from(updated), "Role updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateRole(@PathVariable UUID id) {
        log.info("Deactivating role: id={}", id);

        roleService.deactivate(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Role deactivated successfully"));
    }
}

