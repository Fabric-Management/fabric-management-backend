package com.fabricmanagement.common.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.user.app.UserWorkLocationService;
import com.fabricmanagement.common.platform.user.domain.UserWorkLocation;
import com.fabricmanagement.common.platform.user.dto.AssignWorkLocationRequest;
import com.fabricmanagement.common.platform.user.dto.UserWorkLocationDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/users/{userId}/work-locations")
@RequiredArgsConstructor
@Slf4j
public class UserWorkLocationController {

  private final UserWorkLocationService userWorkLocationService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserWorkLocationDto>>> getUserWorkLocations(
      @PathVariable UUID userId) {
    log.debug("Getting work locations: userId={}", userId);

    List<UserWorkLocationDto> locations =
        userWorkLocationService.getUserLocations(userId).stream()
            .map(UserWorkLocationDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(locations));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<UserWorkLocationDto>> getPrimaryWorkLocation(
      @PathVariable UUID userId) {
    log.debug("Getting primary work location: userId={}", userId);

    return userWorkLocationService
        .getPrimaryLocation(userId)
        .map(primary -> ResponseEntity.ok(ApiResponse.success(UserWorkLocationDto.from(primary))))
        .orElseGet(() -> ResponseEntity.ok(ApiResponse.<UserWorkLocationDto>success(null)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<UserWorkLocationDto>> assignWorkLocation(
      @PathVariable UUID userId, @Valid @RequestBody AssignWorkLocationRequest request) {
    log.info(
        "Assigning work location: userId={}, orgAddressId={}, isPrimary={}",
        userId,
        request.getOrgAddressId(),
        request.getIsPrimary());

    UserWorkLocation wl =
        userWorkLocationService.assignLocation(
            userId, request.getOrgAddressId(), request.getIsPrimary(), request.getNotes());

    return ResponseEntity.ok(
        ApiResponse.success(UserWorkLocationDto.from(wl), "Work location assigned successfully"));
  }

  @PutMapping("/{orgAddressId}/primary")
  public ResponseEntity<ApiResponse<UserWorkLocationDto>> setPrimaryWorkLocation(
      @PathVariable UUID userId, @PathVariable UUID orgAddressId) {
    log.info("Setting primary work location: userId={}, orgAddressId={}", userId, orgAddressId);

    UserWorkLocation wl = userWorkLocationService.setPrimary(userId, orgAddressId);

    return ResponseEntity.ok(
        ApiResponse.success(
            UserWorkLocationDto.from(wl), "Primary work location set successfully"));
  }

  @DeleteMapping("/{orgAddressId}")
  public ResponseEntity<ApiResponse<Void>> removeWorkLocation(
      @PathVariable UUID userId, @PathVariable UUID orgAddressId) {
    log.info("Removing work location: userId={}, orgAddressId={}", userId, orgAddressId);

    userWorkLocationService.removeLocation(userId, orgAddressId);

    return ResponseEntity.ok(ApiResponse.success(null, "Work location removed successfully"));
  }
}
