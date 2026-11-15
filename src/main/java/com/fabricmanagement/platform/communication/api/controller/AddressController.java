package com.fabricmanagement.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.communication.api.facade.AddressFacade;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/common/addresses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Address", description = "Address operations")
public class AddressController {

  private final AddressFacade facade;

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AddressDto>> getAddress(@PathVariable UUID id) {
    log.debug("Getting address: id={}", id);
    return ResponseEntity.ok(ApiResponse.success(facade.getAddress(id)));
  }

  @GetMapping("/type/{type}")
  public ResponseEntity<ApiResponse<List<AddressDto>>> getAddressesByType(
      @PathVariable String type) {
    log.debug("Getting addresses by type: type={}", type);
    AddressType addressType = AddressType.valueOf(type.toUpperCase());
    return ResponseEntity.ok(ApiResponse.success(facade.getAddressesByType(addressType)));
  }
}
