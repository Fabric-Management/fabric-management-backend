package com.fabricmanagement.platform.user.api.facade;

import com.fabricmanagement.platform.user.app.UserWorkLocationService;
import com.fabricmanagement.platform.user.dto.UserWorkLocationDto;
import com.fabricmanagement.platform.user.mapper.UserWorkLocationMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserWorkLocationFacade {

  private final UserWorkLocationService userWorkLocationService;
  private final UserWorkLocationMapper userWorkLocationMapper;

  @Transactional(readOnly = true)
  public List<UserWorkLocationDto> getUserLocations(UUID userId) {
    return userWorkLocationMapper.toDtoList(userWorkLocationService.getUserLocations(userId));
  }

  @Transactional(readOnly = true)
  public Optional<UserWorkLocationDto> getPrimaryLocation(UUID userId) {
    return userWorkLocationService.getPrimaryLocation(userId).map(userWorkLocationMapper::toDto);
  }

  @Transactional
  public UserWorkLocationDto assignLocation(
      UUID userId, UUID orgAddressId, Boolean isPrimary, String notes) {
    return userWorkLocationMapper.toDto(
        userWorkLocationService.assignLocation(userId, orgAddressId, isPrimary, notes));
  }

  @Transactional
  public UserWorkLocationDto setPrimary(UUID userId, UUID orgAddressId) {
    return userWorkLocationMapper.toDto(userWorkLocationService.setPrimary(userId, orgAddressId));
  }

  @Transactional
  public void removeLocation(UUID userId, UUID orgAddressId) {
    userWorkLocationService.removeLocation(userId, orgAddressId);
  }
}
