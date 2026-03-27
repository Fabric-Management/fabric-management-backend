package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.AssignAddressRequest;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.user.app.UserAddressAssignmentService;
import com.fabricmanagement.platform.user.domain.UserAddress;
import com.fabricmanagement.platform.user.dto.UserAddressDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/users/{userId}/addresses")
@RequiredArgsConstructor
@Slf4j
public class UserAddressController {

  private final UserAddressAssignmentService userAddressAssignmentService;
  private final AddressService addressService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserAddressDto>>> getUserAddresses(
      @PathVariable UUID userId) {
    log.debug("Getting user addresses: userId={}", userId);

    List<UserAddressDto> addresses =
        userAddressAssignmentService.getUserAddresses(userId).stream()
            .map(UserAddressDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<UserAddressDto>> getPrimaryAddress(@PathVariable UUID userId) {
    log.debug("Getting primary address: userId={}", userId);

    UserAddress primaryAddress =
        userAddressAssignmentService
            .getPrimaryAddress(userId)
            .orElseThrow(() -> new IllegalArgumentException("No primary address found"));

    return ResponseEntity.ok(ApiResponse.success(UserAddressDto.from(primaryAddress)));
  }

  @GetMapping("/work")
  public ResponseEntity<ApiResponse<List<UserAddressDto>>> getWorkAddresses(
      @PathVariable UUID userId) {
    log.debug("Getting work addresses: userId={}", userId);

    List<UserAddressDto> addresses =
        userAddressAssignmentService.getWorkAddresses(userId).stream()
            .map(UserAddressDto::from)
            .collect(Collectors.toList());

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

    UserAddress userAddress =
        userAddressAssignmentService.assignAddress(
            userId, request.getAddressId(), request.getIsPrimary(), request.getIsWorkAddress());

    return ResponseEntity.ok(
        ApiResponse.success(UserAddressDto.from(userAddress), "Address assigned successfully"));
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

    Address address = addressService.createAddress(createRequest);
    UserAddress userAddress =
        userAddressAssignmentService.assignAddress(
            userId, address.getId(), isPrimary, isWorkAddress);

    return ResponseEntity.ok(
        ApiResponse.success(
            UserAddressDto.from(userAddress), "Address created and assigned successfully"));
  }

  @PutMapping("/{addressId}/primary")
  public ResponseEntity<ApiResponse<UserAddressDto>> setAsPrimary(
      @PathVariable UUID userId, @PathVariable UUID addressId) {
    log.info("Setting primary address: userId={}, addressId={}", userId, addressId);

    UserAddress userAddress = userAddressAssignmentService.setAsPrimary(userId, addressId);

    return ResponseEntity.ok(
        ApiResponse.success(UserAddressDto.from(userAddress), "Primary address set successfully"));
  }

  @DeleteMapping("/{addressId}")
  public ResponseEntity<ApiResponse<Void>> removeAddress(
      @PathVariable UUID userId, @PathVariable UUID addressId) {
    log.info("Removing address from user: userId={}, addressId={}", userId, addressId);

    userAddressAssignmentService.removeAddress(userId, addressId);

    return ResponseEntity.ok(ApiResponse.success(null, "Address removed successfully"));
  }
}
