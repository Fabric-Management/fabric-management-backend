package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.AddressContactService;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.UserAddressService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressContact;
import com.fabricmanagement.common.platform.communication.domain.UserAddress;
import com.fabricmanagement.common.platform.communication.dto.AddressContactDto;
import com.fabricmanagement.common.platform.communication.dto.AssignAddressContactRequest;
import com.fabricmanagement.common.platform.communication.dto.AssignAddressRequest;
import com.fabricmanagement.common.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.common.platform.communication.dto.UserAddressDto;
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

  private final UserAddressService userAddressService;
  private final AddressService addressService;
  private final AddressContactService addressContactService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserAddressDto>>> getUserAddresses(
      @PathVariable UUID userId) {
    log.debug("Getting user addresses: userId={}", userId);

    List<UserAddressDto> addresses =
        userAddressService.getUserAddresses(userId).stream()
            .map(UserAddressDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<UserAddressDto>> getPrimaryAddress(@PathVariable UUID userId) {
    log.debug("Getting primary address: userId={}", userId);

    UserAddress primaryAddress =
        userAddressService
            .getPrimaryAddress(userId)
            .orElseThrow(() -> new IllegalArgumentException("No primary address found"));

    return ResponseEntity.ok(ApiResponse.success(UserAddressDto.from(primaryAddress)));
  }

  @GetMapping("/work")
  public ResponseEntity<ApiResponse<List<UserAddressDto>>> getWorkAddresses(
      @PathVariable UUID userId) {
    log.debug("Getting work addresses: userId={}", userId);

    List<UserAddressDto> addresses =
        userAddressService.getWorkAddresses(userId).stream()
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
        userAddressService.assignAddress(
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

    // Create address first
    Address address =
        addressService.createAddress(
            createRequest.getStreetAddress(),
            createRequest.getCity(),
            createRequest.getState(),
            createRequest.getPostalCode(),
            createRequest.getCountry(),
            createRequest.getAddressType(),
            createRequest.getLabel());

    // Assign to user
    UserAddress userAddress =
        userAddressService.assignAddress(userId, address.getId(), isPrimary, isWorkAddress);

    return ResponseEntity.ok(
        ApiResponse.success(
            UserAddressDto.from(userAddress), "Address created and assigned successfully"));
  }

  @PutMapping("/{addressId}/primary")
  public ResponseEntity<ApiResponse<UserAddressDto>> setAsPrimary(
      @PathVariable UUID userId, @PathVariable UUID addressId) {
    log.info("Setting primary address: userId={}, addressId={}", userId, addressId);

    UserAddress userAddress = userAddressService.setAsPrimary(userId, addressId);

    return ResponseEntity.ok(
        ApiResponse.success(UserAddressDto.from(userAddress), "Primary address set successfully"));
  }

  @DeleteMapping("/{addressId}")
  public ResponseEntity<ApiResponse<Void>> removeAddress(
      @PathVariable UUID userId, @PathVariable UUID addressId) {
    log.info("Removing address from user: userId={}, addressId={}", userId, addressId);

    userAddressService.removeAddress(userId, addressId);

    return ResponseEntity.ok(ApiResponse.success(null, "Address removed successfully"));
  }

  // =========================================================================
  // ADDRESS CONTACT ENDPOINTS
  // =========================================================================

  @GetMapping("/{addressId}/contacts")
  public ResponseEntity<ApiResponse<List<AddressContactDto>>> getAddressContacts(
      @PathVariable UUID userId, @PathVariable UUID addressId) {
    log.debug("Getting contacts for user address: userId={}, addressId={}", userId, addressId);

    List<AddressContactDto> contacts =
        userAddressService.getAddressContacts(userId, addressId).stream()
            .map(AddressContactDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  @GetMapping("/{addressId}/contacts/primary")
  public ResponseEntity<ApiResponse<AddressContactDto>> getPrimaryContact(
      @PathVariable UUID userId, @PathVariable UUID addressId) {
    log.debug(
        "Getting primary contact for user address: userId={}, addressId={}", userId, addressId);

    AddressContact primaryContact =
        userAddressService
            .getPrimaryContact(userId, addressId)
            .orElseThrow(
                () -> new IllegalArgumentException("No primary contact found for this address"));

    return ResponseEntity.ok(ApiResponse.success(AddressContactDto.from(primaryContact)));
  }

  @PostMapping("/{addressId}/contacts")
  public ResponseEntity<ApiResponse<AddressContactDto>> assignContact(
      @PathVariable UUID userId,
      @PathVariable UUID addressId,
      @Valid @RequestBody AssignAddressContactRequest request) {
    log.info(
        "Assigning contact to user address: userId={}, addressId={}, contactId={}, isPrimary={}",
        userId,
        addressId,
        request.getContactId(),
        request.getIsPrimary());

    // Verify address belongs to user
    userAddressService.getUserAddresses(userId).stream()
        .filter(ua -> ua.getAddressId().equals(addressId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Address is not assigned to this user"));

    AddressContact addressContact =
        addressContactService.assignContact(
            addressId, request.getContactId(), request.getIsPrimary(), request.getLabel());

    return ResponseEntity.ok(
        ApiResponse.success(
            AddressContactDto.from(addressContact), "Contact assigned to address successfully"));
  }

  @PutMapping("/{addressId}/contacts/{contactId}/primary")
  public ResponseEntity<ApiResponse<AddressContactDto>> setContactAsPrimary(
      @PathVariable UUID userId, @PathVariable UUID addressId, @PathVariable UUID contactId) {
    log.info(
        "Setting primary contact: userId={}, addressId={}, contactId={}",
        userId,
        addressId,
        contactId);

    // Verify address belongs to user
    userAddressService.getUserAddresses(userId).stream()
        .filter(ua -> ua.getAddressId().equals(addressId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Address is not assigned to this user"));

    AddressContact addressContact = addressContactService.setAsPrimary(addressId, contactId);

    return ResponseEntity.ok(
        ApiResponse.success(
            AddressContactDto.from(addressContact), "Primary contact set successfully"));
  }

  @DeleteMapping("/{addressId}/contacts/{contactId}")
  public ResponseEntity<ApiResponse<Void>> removeContact(
      @PathVariable UUID userId, @PathVariable UUID addressId, @PathVariable UUID contactId) {
    log.info(
        "Removing contact from user address: userId={}, addressId={}, contactId={}",
        userId,
        addressId,
        contactId);

    // Verify address belongs to user
    userAddressService.getUserAddresses(userId).stream()
        .filter(ua -> ua.getAddressId().equals(addressId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Address is not assigned to this user"));

    addressContactService.removeContact(addressId, contactId);

    return ResponseEntity.ok(
        ApiResponse.success(null, "Contact removed from address successfully"));
  }
}
