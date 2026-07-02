package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.common.util.DeviceInfoUtil;
import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.OrganizationMembershipDto;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists and switches organization memberships for the current login identity. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwitchOrganizationService {

  private final MembershipRepository membershipRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final UserFacade userFacade;
  private final TenantQueryPort tenantQueryPort;
  private final TenantSessionBinder tenantSessionBinder;
  private final JwtService jwtService;

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpiration;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  @Transactional(readOnly = true)
  public List<OrganizationMembershipDto> getMemberships(UUID currentUserId, UUID currentTenantId) {
    return membershipRepository
        .findByUserId(currentUserId)
        .map(currentMembership -> getIdentityMemberships(currentMembership, currentTenantId))
        .orElseGet(() -> fallbackCurrentMembership(currentUserId, currentTenantId));
  }

  @Transactional
  public LoginResponse switchOrganization(
      UUID currentUserId, UUID targetTenantId, String ipAddress, String userAgent) {
    Membership currentMembership =
        membershipRepository.findByUserId(currentUserId).orElseThrow(this::membershipNotFound);

    Membership targetMembership =
        membershipRepository
            .findByLoginIdentityIdAndTenantId(
                currentMembership.getLoginIdentityId(), targetTenantId)
            .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
            .orElseThrow(this::membershipNotFound);

    TenantContext.setCurrentTenantId(targetTenantId);
    tenantSessionBinder.bindToCurrentSession(targetTenantId);

    try {
      User targetUser =
          userRepository
              .findByTenantIdAndId(targetTenantId, targetMembership.getUserId())
              .orElseThrow(() -> new IllegalArgumentException("User entity not found"));

      if (!Boolean.TRUE.equals(targetUser.getIsActive())) {
        throw new PlatformDomainException(
            "User account is deactivated", "AUTH_USER_DEACTIVATED", 403);
      }

      String accessToken = jwtService.generateAccessToken(targetUser);
      String refreshToken = jwtService.generateRefreshToken(targetUser);

      RefreshToken refreshTokenEntity =
          RefreshToken.create(
              targetUser.getId(),
              refreshToken,
              Instant.now().plusMillis(refreshTokenExpiration),
              ipAddress,
              userAgent,
              DeviceInfoUtil.extractDeviceName(userAgent));
      refreshTokenRepository.save(refreshTokenEntity);

      UserDto userDto =
          userFacade
              .findById(targetTenantId, targetUser.getId())
              .orElseThrow(() -> new IllegalArgumentException("User DTO not found"));

      log.info(
          "Organization switch successful: currentUserId={}, targetTenantId={}, targetUserId={}",
          currentUserId,
          targetTenantId,
          targetUser.getId());

      return LoginResponse.builder()
          .accessToken(accessToken)
          .refreshToken(refreshToken)
          .expiresIn(accessTokenExpiration / 1000)
          .user(userDto)
          .needsOnboarding(!Boolean.TRUE.equals(userDto.getHasCompletedOnboarding()))
          .build();
    } finally {
      TenantContext.clear();
    }
  }

  private List<OrganizationMembershipDto> getIdentityMemberships(
      Membership currentMembership, UUID currentTenantId) {
    List<Membership> activeMemberships =
        membershipRepository.findByLoginIdentityIdAndStatus(
            currentMembership.getLoginIdentityId(), MembershipStatus.ACTIVE);

    if (activeMemberships.isEmpty()) {
      return fallbackCurrentMembership(currentMembership.getUserId(), currentTenantId);
    }

    Map<UUID, TenantReference> tenantsById =
        tenantQueryPort
            .findAllByIds(activeMemberships.stream().map(Membership::getTenantId).toList())
            .stream()
            .collect(Collectors.toMap(TenantReference::id, Function.identity()));

    return activeMemberships.stream()
        .map(
            membership ->
                toDto(membership, currentTenantId, tenantsById.get(membership.getTenantId())))
        .sorted(
            Comparator.comparing((OrganizationMembershipDto dto) -> !dto.isCurrent())
                .thenComparing(
                    OrganizationMembershipDto::tenantName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private List<OrganizationMembershipDto> fallbackCurrentMembership(
      UUID currentUserId, UUID currentTenantId) {
    String tenantName =
        tenantQueryPort
            .findById(currentTenantId)
            .map(this::displayName)
            .orElse(currentTenantId.toString());
    return List.of(
        new OrganizationMembershipDto(currentTenantId, tenantName, currentUserId, true, true));
  }

  private OrganizationMembershipDto toDto(
      Membership membership, UUID currentTenantId, TenantReference tenant) {
    return new OrganizationMembershipDto(
        membership.getTenantId(),
        tenant != null ? displayName(tenant) : membership.getTenantId().toString(),
        membership.getUserId(),
        membership.getTenantId().equals(currentTenantId),
        Boolean.TRUE.equals(membership.getIsDefault()));
  }

  private String displayName(TenantReference tenant) {
    if (tenant.name() != null && !tenant.name().isBlank()) {
      return tenant.name();
    }
    if (tenant.uid() != null && !tenant.uid().isBlank()) {
      return tenant.uid();
    }
    return tenant.id().toString();
  }

  private PlatformDomainException membershipNotFound() {
    return new PlatformDomainException(
        "Organization membership not found", "AUTH_MEMBERSHIP_NOT_FOUND", 403);
  }
}
