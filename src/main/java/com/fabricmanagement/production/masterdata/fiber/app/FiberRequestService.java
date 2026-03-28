package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import com.fabricmanagement.platform.communication.dto.NotificationRequest;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberRequest;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberRequestStatus;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequestRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberRequestDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRequestRepository;
import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.infra.repository.MaterialRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fiber request service - Handles fiber request workflow (submit, approve, reject) and sends
 * notifications.
 *
 * <p>Notification triggers:
 *
 * <ul>
 *   <li>onSubmit: Tenant submits → Platform admins notified (IN_APP)
 *   <li>onApprove: Platform approves → Requester tenant notified (BOTH)
 *   <li>onReject: Platform rejects → Requester tenant notified (BOTH)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberRequestService {

  private static final int MIN_REVIEW_NOTE_LENGTH = 10;

  private final FiberRequestRepository fiberRequestRepository;
  private final FiberIsoCodeRepository fiberIsoCodeRepository;
  private final FiberCategoryRepository fiberCategoryRepository;
  private final MaterialRepository materialRepository;
  private final FiberRepository fiberRepository;
  private final InAppNotificationService notificationService;
  private final TenantRepository tenantRepository;

  /**
   * Submit a fiber request (tenant → platform).
   *
   * <p>Duplicate check: PENDING or APPROVED with same isoCode for tenant → error.
   *
   * <p>FiberIsoCode duplicate: prod_fiber_iso_code already has isoCode → error.
   *
   * @param request Create request
   * @param tenantId Current tenant ID
   * @param userId Requesting user ID
   * @return Created fiber request
   */
  @Transactional
  public FiberRequestDto submit(CreateFiberRequestRequest request, UUID tenantId, UUID userId) {
    String isoCode = request.getIsoCode().trim().toUpperCase();

    // Duplicate: PENDING or APPROVED with same isoCode for this tenant (case-insensitive)
    if (fiberRequestRepository.existsByTenantIdAndIsoCodeIgnoreCaseAndStatusIn(
        tenantId, isoCode, List.of(FiberRequestStatus.PENDING, FiberRequestStatus.APPROVED))) {
      throw new FiberDomainException(
          "A fiber request with this ISO code already exists (PENDING or APPROVED)",
          "FIBER_REQUEST_DUPLICATE_PENDING",
          409,
          new Object[] {isoCode});
    }

    // FiberIsoCode already exists in catalog (case-insensitive)
    if (fiberIsoCodeRepository.existsByIsoCodeIgnoreCase(isoCode)) {
      throw new FiberDomainException(
          "ISO code already exists in the fiber catalog",
          "FIBER_REQUEST_DUPLICATE_CATALOG",
          409,
          new Object[] {isoCode});
    }

    FiberRequest entity =
        FiberRequest.builder()
            .requestedBy(userId)
            .isoCode(isoCode)
            .fiberName(request.getFiberName().trim())
            .fiberType(request.getFiberType().trim())
            .description(request.getDescription() != null ? request.getDescription().trim() : null)
            .status(FiberRequestStatus.PENDING)
            .build();

    FiberRequest saved = fiberRequestRepository.save(entity);
    sendOnSubmitNotification(
        saved.getId(), saved.getTenantId(), saved.getIsoCode(), saved.getFiberName());

    log.info(
        "Fiber request submitted: id={}, isoCode={}, fiberName={}",
        saved.getId(),
        isoCode,
        saved.getFiberName());
    return FiberRequestDto.from(saved);
  }

  /**
   * Approve a fiber request (platform only).
   *
   * <p>Creates FiberIsoCode, Material, Fiber in system tenant context.
   *
   * @param requestId Fiber request ID
   * @param reviewedBy Platform reviewer user ID
   * @return Updated fiber request
   */
  @Transactional
  public FiberRequestDto approve(UUID requestId, UUID reviewedBy) {
    FiberRequest request =
        fiberRequestRepository
            .findById(requestId)
            .orElseThrow(
                () ->
                    new FiberDomainException(
                        "Fiber request not found: " + requestId, "FIBER_REQUEST_NOT_FOUND", 404));

    if (request.getStatus() != FiberRequestStatus.PENDING) {
      throw new FiberDomainException(
          "Only PENDING requests can be approved.",
          "FIBER_REQUEST_INVALID_STATUS",
          409,
          new Object[] {request.getStatus()});
    }

    createFiberFromRequest(request);

    request.setStatus(FiberRequestStatus.APPROVED);
    request.setReviewedBy(reviewedBy);
    request.setReviewNote(null);
    FiberRequest saved = fiberRequestRepository.save(request);

    sendOnApproveNotification(
        saved.getId(), saved.getTenantId(), saved.getIsoCode(), saved.getFiberName());

    log.info("Fiber request approved: id={}, isoCode={}", requestId, saved.getIsoCode());
    return FiberRequestDto.from(saved);
  }

  /**
   * Reject a fiber request (platform only).
   *
   * @param requestId Fiber request ID
   * @param reviewNote Rejection reason (min 10 characters)
   * @param reviewedBy Platform reviewer user ID
   * @return Updated fiber request
   */
  @Transactional
  public FiberRequestDto reject(UUID requestId, String reviewNote, UUID reviewedBy) {
    FiberRequest request =
        fiberRequestRepository
            .findById(requestId)
            .orElseThrow(
                () ->
                    new FiberDomainException(
                        "Fiber request not found: " + requestId, "FIBER_REQUEST_NOT_FOUND", 404));

    if (request.getStatus() != FiberRequestStatus.PENDING) {
      throw new FiberDomainException(
          "Only PENDING requests can be rejected.",
          "FIBER_REQUEST_INVALID_STATUS",
          409,
          new Object[] {request.getStatus()});
    }

    if (reviewNote == null || reviewNote.trim().length() < MIN_REVIEW_NOTE_LENGTH) {
      throw new FiberDomainException(
          "Review note is required and must be at least the minimum length.",
          "FIBER_REQUEST_REVIEW_NOTE_TOO_SHORT",
          400,
          new Object[] {MIN_REVIEW_NOTE_LENGTH});
    }

    request.setStatus(FiberRequestStatus.REJECTED);
    request.setReviewedBy(reviewedBy);
    request.setReviewNote(reviewNote.trim());
    FiberRequest saved = fiberRequestRepository.save(request);

    sendOnRejectNotification(
        saved.getId(),
        saved.getTenantId(),
        saved.getIsoCode(),
        saved.getFiberName(),
        saved.getReviewNote());

    log.info("Fiber request rejected: id={}, isoCode={}", requestId, saved.getIsoCode());
    return FiberRequestDto.from(saved);
  }

  /** List fiber requests by tenant. */
  @Transactional(readOnly = true)
  public Page<FiberRequestDto> listByTenant(UUID tenantId, Pageable pageable) {
    return fiberRequestRepository
        .findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
        .map(FiberRequestDto::from);
  }

  /**
   * List fiber requests for platform admin (optional status filter).
   *
   * @param statusFilter Empty = all, PENDING/APPROVED/REJECTED = filter by status
   */
  @Transactional(readOnly = true)
  public Page<FiberRequestDto> listForPlatform(
      Optional<FiberRequestStatus> statusFilter, Pageable pageable) {
    Page<FiberRequest> page =
        statusFilter
            .map(s -> fiberRequestRepository.findByStatusOrderByCreatedAtDesc(s, pageable))
            .orElseGet(() -> fiberRequestRepository.findAll(pageable));

    if (page.isEmpty()) {
      return page.map(e -> FiberRequestDto.from(e, null));
    }

    Set<UUID> tenantIds =
        page.getContent().stream().map(FiberRequest::getTenantId).collect(Collectors.toSet());
    Map<UUID, String> tenantNames =
        tenantRepository.findAllById(tenantIds).stream()
            .collect(
                Collectors.toMap(
                    com.fabricmanagement.platform.tenant.domain.Tenant::getId,
                    com.fabricmanagement.platform.tenant.domain.Tenant::getName));

    return page.map(
        e ->
            FiberRequestDto.from(
                e, tenantNames.getOrDefault(e.getTenantId(), e.getTenantId().toString())));
  }

  /** Get fiber request by ID (tenant-scoped). */
  @Transactional(readOnly = true)
  public Optional<FiberRequestDto> getByIdForTenant(UUID tenantId, UUID id) {
    return fiberRequestRepository.findByTenantIdAndId(tenantId, id).map(FiberRequestDto::from);
  }

  /** Get fiber request by ID (platform — any tenant, includes tenantName). */
  @Transactional(readOnly = true)
  public Optional<FiberRequestDto> getById(UUID id) {
    return fiberRequestRepository
        .findById(id)
        .map(
            e -> {
              String tenantName =
                  tenantRepository
                      .findById(e.getTenantId())
                      .map(com.fabricmanagement.platform.tenant.domain.Tenant::getName)
                      .orElse(e.getTenantId().toString());
              return FiberRequestDto.from(e, tenantName);
            });
  }

  /**
   * Create FiberIsoCode, Material, Fiber from approved request (R__001 logic).
   *
   * <p>Runs in SYSTEM_TENANT context so entities are platform-level.
   */
  private void createFiberFromRequest(FiberRequest request) {
    TenantContext.executeInTenantContext(
        TenantContext.SYSTEM_TENANT_ID,
        () -> {
          // 1. prod_fiber_iso_code INSERT
          FiberIsoCode isoCode =
              FiberIsoCode.builder()
                  .isoCode(request.getIsoCode())
                  .fiberName(request.getFiberName())
                  .fiberType(request.getFiberType())
                  .description(request.getDescription())
                  .isOfficialIso(false)
                  .build();
          isoCode = fiberIsoCodeRepository.save(isoCode);

          // 2. prod_material INSERT
          Material material = Material.create(MaterialType.FIBER, "KG");
          material = materialRepository.save(material);

          // 3. prod_fiber INSERT — resolve category by fiber_type (category_code)
          FiberCategory category =
              fiberCategoryRepository
                  .findByCategoryCode(request.getFiberType())
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              "Fiber category not found for fiber_type: "
                                  + request.getFiberType()));

          String fiberName = request.getFiberName() + " (100%)";
          Fiber fiber = Fiber.createPureFiber(material, category, isoCode, fiberName);
          fiberRepository.save(fiber);

          log.info(
              "Created fiber from request: isoCode={}, materialId={}, fiberId={}",
              request.getIsoCode(),
              material.getId(),
              fiber.getId());
        });
  }

  private void sendOnSubmitNotification(
      UUID fiberRequestId, UUID tenantId, String isoCode, String fiberName) {
    String tenantName =
        tenantRepository.findById(tenantId).map(t -> t.getName()).orElse(tenantId.toString());
    String message = isoCode + " — " + fiberName + " requested by " + tenantName;

    notificationService.send(
        NotificationRequest.builder()
            .tenantId(TenantContext.SYSTEM_TENANT_ID)
            .recipientId(null)
            .type(NotificationType.FIBER_REQUEST_SUBMITTED)
            .title("New Fiber Request")
            .message(message)
            .referenceId(fiberRequestId)
            .referenceType("FIBER_REQUEST")
            .channel(NotificationDeliveryChannel.IN_APP)
            .build());

    log.info(
        "Fiber request submitted notification sent: fiberRequestId={}, tenant={}",
        fiberRequestId,
        tenantName);
  }

  private void sendOnApproveNotification(
      UUID fiberRequestId, UUID tenantId, String isoCode, String fiberName) {
    String message = isoCode + " — " + fiberName + " has been added to the catalog";

    notificationService.send(
        NotificationRequest.builder()
            .tenantId(tenantId)
            .recipientId(null)
            .type(NotificationType.FIBER_REQUEST_APPROVED)
            .title("Fiber Request Approved")
            .message(message)
            .referenceId(fiberRequestId)
            .referenceType("FIBER_REQUEST")
            .channel(NotificationDeliveryChannel.BOTH)
            .build());

    log.info(
        "Fiber request approved notification sent: fiberRequestId={}, tenantId={}",
        fiberRequestId,
        tenantId);
  }

  private void sendOnRejectNotification(
      UUID fiberRequestId, UUID tenantId, String isoCode, String fiberName, String reviewNote) {
    String message =
        isoCode + " — " + fiberName + ": " + (reviewNote != null ? reviewNote : "Request rejected");

    notificationService.send(
        NotificationRequest.builder()
            .tenantId(tenantId)
            .recipientId(null)
            .type(NotificationType.FIBER_REQUEST_REJECTED)
            .title("Fiber Request Rejected")
            .message(message)
            .referenceId(fiberRequestId)
            .referenceType("FIBER_REQUEST")
            .channel(NotificationDeliveryChannel.BOTH)
            .build());

    log.info(
        "Fiber request rejected notification sent: fiberRequestId={}, tenantId={}",
        fiberRequestId,
        tenantId);
  }
}
