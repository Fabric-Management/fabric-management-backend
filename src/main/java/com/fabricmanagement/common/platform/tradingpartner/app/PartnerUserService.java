package com.fabricmanagement.common.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.common.platform.tradingpartner.domain.event.PartnerUserCreatedEvent;
import com.fabricmanagement.common.platform.tradingpartner.dto.InvitePartnerUserRequest;
import com.fabricmanagement.common.platform.tradingpartner.dto.PartnerUserDto;
import com.fabricmanagement.common.platform.tradingpartner.dto.UpdatePartnerUserRoleRequest;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.common.platform.user.app.RoleService;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing partner portal users.
 *
 * <p>Delegates user creation to the existing {@link
 * com.fabricmanagement.common.platform.user.app.UserCreationService} via {@link
 * com.fabricmanagement.common.platform.user.app.UserService} but operates in the context of a
 * specific {@link TradingPartner} relationship. All mutating operations enforce partner isolation:
 * the requested {@code partnerId} must resolve to a {@link TradingPartner} owned by the current
 * tenant, and the target user must belong to that partner's organization.
 *
 * <p>Status derivation:
 *
 * <ul>
 *   <li>{@code INVITED} — User exists but has no {@link AuthUser} (setup link not clicked yet)
 *   <li>{@code SUSPENDED} — User is soft-deleted ({@code isActive = false})
 *   <li>{@code ACTIVE} — User has {@link AuthUser} and is active
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerUserService {

  private static final Set<String> PARTNER_ROLE_CODES =
      Set.of("PARTNER_OWNER", "PARTNER_ACCOUNTANT", "PARTNER_BUYER", "PARTNER_VIEWER");

  private final TradingPartnerRepository tradingPartnerRepository;
  private final UserRepository userRepository;
  private final AuthUserRepository authUserRepository;
  private final RoleService roleService;
  private final DomainEventPublisher eventPublisher;
  private final com.fabricmanagement.common.platform.user.app.UserService userService;

  // ── Public API ──────────────────────────────────────────────────────────────

  /**
   * Invite a new user to the partner portal.
   *
   * <p>Creates a {@link User} record under the partner's organisation, assigns the requested
   * partner role, then publishes {@link PartnerUserCreatedEvent} which triggers the invitation
   * email via {@link PartnerUserInvitationEventListener}.
   *
   * @param partnerId TradingPartner UUID (tenant-scoped)
   * @param request Invitation details
   * @return Created partner user DTO
   */
  @Transactional
  public PartnerUserDto inviteUser(UUID partnerId, InvitePartnerUserRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    TradingPartner partner = resolvePartner(tenantId, partnerId);
    validatePartnerRoleCode(request.getPartnerRoleCode());

    log.info(
        "Inviting partner user: partnerId={}, email={}",
        partnerId,
        PiiMaskingUtil.maskEmail(request.getEmail()));

    CreateExternalUserRequest createRequest =
        CreateExternalUserRequest.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .contactValue(request.getEmail())
            .contactType(com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL)
            .companyId(partner.getOrganizationId())
            .suppressEmailInvitation(true) // partner listener sends its own invitation email
            .build();

    // Delegate to existing external user creation (handles uniqueness check, contact assignment)
    com.fabricmanagement.common.platform.user.dto.UserDto userDto =
        userService.createExternalUser(createRequest);

    // Assign the partner role
    Role role =
        roleService
            .findByCode(request.getPartnerRoleCode())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Partner role not found: " + request.getPartnerRoleCode()));

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userDto.getId())
            .orElseThrow(() -> new IllegalStateException("User not found after creation"));
    user.setRole(role);
    userRepository.save(user);

    // Publish partner-specific event — bypasses generic UserInvitationEventListener
    eventPublisher.publish(
        new PartnerUserCreatedEvent(
            tenantId,
            user.getId(),
            user.getDisplayName(),
            request.getEmail(),
            partnerId,
            partner.getOrganizationId(),
            partner.getDisplayName()));

    log.info(
        "Partner user invited: userId={}, partnerId={}, role={}",
        user.getId(),
        partnerId,
        request.getPartnerRoleCode());

    return toDto(user, partnerId, "INVITED");
  }

  /**
   * List all partner portal users for a trading partner.
   *
   * @param partnerId TradingPartner UUID
   * @return List of partner users (only those with PARTNER_* roles)
   */
  @Transactional(readOnly = true)
  public List<PartnerUserDto> listUsers(UUID partnerId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    TradingPartner partner = resolvePartner(tenantId, partnerId);

    // Include both active and suspended users so the UI can show all states
    List<User> allOrgUsers =
        userRepository.findByTenantIdAndOrganizationId(tenantId, partner.getOrganizationId());

    return allOrgUsers.stream()
        .filter(u -> u.getRole() != null && PARTNER_ROLE_CODES.contains(u.getRole().getRoleCode()))
        .map(u -> toDto(u, partnerId, deriveStatus(u)))
        .collect(Collectors.toList());
  }

  /**
   * Update the partner role of an existing partner user.
   *
   * @param partnerId TradingPartner UUID
   * @param userId User UUID
   * @param request New role
   * @return Updated partner user DTO
   */
  @Transactional
  public PartnerUserDto updateRole(
      UUID partnerId, UUID userId, UpdatePartnerUserRoleRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    TradingPartner partner = resolvePartner(tenantId, partnerId);
    User user = resolvePartnerUser(tenantId, partner, userId);

    validatePartnerRoleCode(request.getPartnerRoleCode());
    Role role =
        roleService
            .findByCode(request.getPartnerRoleCode())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Partner role not found: " + request.getPartnerRoleCode()));

    user.setRole(role);
    userRepository.save(user);

    log.info(
        "Partner user role updated: userId={}, partnerId={}, newRole={}",
        userId,
        partnerId,
        request.getPartnerRoleCode());

    return toDto(user, partnerId, deriveStatus(user));
  }

  /**
   * Suspend a partner user (soft disable — keeps record).
   *
   * @param partnerId TradingPartner UUID
   * @param userId User UUID
   */
  @Transactional
  public void suspendUser(UUID partnerId, UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    TradingPartner partner = resolvePartner(tenantId, partnerId);
    User user = resolvePartnerUser(tenantId, partner, userId);

    user.delete(); // soft-delete via BaseEntity
    userRepository.save(user);

    log.info("Partner user suspended: userId={}, partnerId={}", userId, partnerId);
  }

  /**
   * Reactivate a previously suspended partner user.
   *
   * @param partnerId TradingPartner UUID
   * @param userId User UUID
   */
  @Transactional
  public PartnerUserDto reactivateUser(UUID partnerId, UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    TradingPartner partner = resolvePartner(tenantId, partnerId);

    // Include inactive users for reactivation
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Partner user not found: " + userId));

    validateUserBelongsToPartner(partner, user);

    user.activate();
    userRepository.save(user);

    log.info("Partner user reactivated: userId={}, partnerId={}", userId, partnerId);
    return toDto(user, partnerId, deriveStatus(user));
  }

  /**
   * Remove a partner user permanently.
   *
   * @param partnerId TradingPartner UUID
   * @param userId User UUID
   */
  @Transactional
  public void removeUser(UUID partnerId, UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    TradingPartner partner = resolvePartner(tenantId, partnerId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Partner user not found: " + userId));

    validateUserBelongsToPartner(partner, user);
    validatePartnerRoleOnUser(user);

    userRepository.delete(user);
    log.info("Partner user removed: userId={}, partnerId={}", userId, partnerId);
  }

  // ── Private helpers ──────────────────────────────────────────────────────────

  private TradingPartner resolvePartner(UUID tenantId, UUID partnerId) {
    return tradingPartnerRepository
        .findByTenantIdAndId(tenantId, partnerId)
        .orElseThrow(() -> new IllegalArgumentException("Trading partner not found: " + partnerId));
  }

  private User resolvePartnerUser(UUID tenantId, TradingPartner partner, UUID userId) {
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Partner user not found: " + userId));
    validateUserBelongsToPartner(partner, user);
    validatePartnerRoleOnUser(user);
    return user;
  }

  private void validateUserBelongsToPartner(TradingPartner partner, User user) {
    if (!partner.getOrganizationId().equals(user.getOrganizationId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "User does not belong to this partner");
    }
  }

  private void validatePartnerRoleOnUser(User user) {
    if (user.getRole() == null || !PARTNER_ROLE_CODES.contains(user.getRole().getRoleCode())) {
      throw new IllegalArgumentException("User is not a partner portal user");
    }
  }

  private void validatePartnerRoleCode(String roleCode) {
    if (!PARTNER_ROLE_CODES.contains(roleCode)) {
      throw new IllegalArgumentException(
          "Invalid partner role code: " + roleCode + ". Must be one of: " + PARTNER_ROLE_CODES);
    }
  }

  private String deriveStatus(User user) {
    if (Boolean.FALSE.equals(user.getIsActive())) {
      return "SUSPENDED";
    }
    if (!authUserRepository.existsByUserId(user.getId())) {
      return "INVITED";
    }
    return "ACTIVE";
  }

  private PartnerUserDto toDto(User user, UUID partnerId, String status) {
    String email = user.getAnyVerifiedContact().map(c -> c.getContactValue()).orElse(null);

    return PartnerUserDto.builder()
        .userId(user.getId())
        .uid(user.getUid())
        .displayName(user.getDisplayName())
        .email(email)
        .partnerRoleCode(user.getRole() != null ? user.getRole().getRoleCode() : null)
        .partnerRoleName(user.getRole() != null ? user.getRole().getRoleName() : null)
        .status(status)
        .lastLoginAt(user.getLastActiveAt())
        .invitedAt(user.getCreatedAt())
        .partnerId(partnerId)
        .organizationId(user.getOrganizationId())
        .build();
  }
}
