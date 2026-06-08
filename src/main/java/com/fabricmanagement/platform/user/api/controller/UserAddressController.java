package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.communication.dto.AssignAddressRequest;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.user.api.facade.UserAddressFacade;
import com.fabricmanagement.platform.user.dto.UserAddressDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/common/users/{userId}/addresses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Address", description = "User Address operations")
public class UserAddressController {

  private final UserAddressFacade userAddressFacade;

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserAddressDto>>> getUserAddresses(
      @PathVariable UUID userId) {
    log.debug("Getting user addresses: userId={}", userId);

    List<UserAddressDto> addresses = userAddressFacade.getUserAddresses(userId);

    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<UserAddressDto>> getPrimaryAddress(@PathVariable UUID userId) {
    log.debug("Getting primary address: userId={}", userId);

    UserAddressDto primaryAddress =
        userAddressFacade
            .getPrimaryAddress(userId)
            .orElseThrow(() -> new IllegalArgumentException("No primary address found"));

    return ResponseEntity.ok(ApiResponse.success(primaryAddress));
  }

  @GetMapping("/work")
  public ResponseEntity<ApiResponse<List<UserAddressDto>>> getWorkAddresses(
      @PathVariable UUID userId) {
    log.debug("Getting work addresses: userId={}", userId);

    List<UserAddressDto> addresses = userAddressFacade.getWorkAddresses(userId);

    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<UserAddressDto>> assignAddress(
      @PathVariable UUID userId, @Valid @RequestBody AssignAddressRequest request) {
    log.info(
        "Assigning address to user: userId={}, addressId={}, isPrimary={}, isWork={}",
        userId,
        request.getAddressId(),
        request.getIsPrimary(),
        request.getIsWorkAddress());

    UserAddressDto userAddress =
        userAddressFacade.assignAddress(
            userId, request.getAddressId(), request.getIsPrimary(), request.getIsWorkAddress());

    return ResponseEntity.ok(ApiResponse.success(userAddress, "Address assigned successfully"));
  }

  @PostMapping("/create-and-assign")
  public ResponseEntity<ApiResponse<UserAddressDto>> createAndAssignAddress(
      @PathVariable UUID userId,
      @Valid @RequestBody CreateAddressRequest createRequest,
      @RequestParam(defaultValue = "false") Boolean isPrimary,
      @RequestParam(defaultValue = "false") Boolean isWorkAddress) {
    log.info(
        "Creating and assigning address to user: userId={}, type={}",
        userId,
        createRequest.getAddressType());

    UserAddressDto userAddress =
        userAddressFacade.createAndAssignAddress(userId, createRequest, isPrimary, isWorkAddress);

    return ResponseEntity.ok(
        ApiResponse.success(userAddress, "Address created and assigned successfully"));
  }

  @PutMapping("/{addressId}/primary")
  public ResponseEntity<ApiResponse<UserAddressDto>> setAsPrimary(
      @PathVariable UUID userId, @PathVariable UUID addressId) {
    log.info("Setting primary address: userId={}, addressId={}", userId, addressId);

    UserAddressDto userAddress = userAddressFacade.setAsPrimary(userId, addressId);

    return ResponseEntity.ok(ApiResponse.success(userAddress, "Primary address set successfully"));
  }

  @DeleteMapping("/{addressId}")
  public ResponseEntity<ApiResponse<Void>> removeAddress(
      @PathVariable UUID userId, @PathVariable UUID addressId) {
    log.info("Removing address from user: userId={}, addressId={}", userId, addressId);

    userAddressFacade.removeAddress(userId, addressId);

    return ResponseEntity.ok(ApiResponse.success(null, "Address removed successfully"));
  }
}
