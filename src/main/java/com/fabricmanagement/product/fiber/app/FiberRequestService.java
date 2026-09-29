package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import com.fabricmanagement.platform.communication.dto.NotificationRequest;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberRequest;
import com.fabricmanagement.product.fiber.domain.FiberRequestStatus;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequestRequest;
import com.fabricmanagement.product.fiber.dto.FiberRequestDto;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRequestRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fiber request workflow (submit, approve, reject) against the shared catalogue (FIBER-CATALOG-1;
 * supersedes FIBER-SRC-1 R3's tenant-local ISO resolution).
 *
 * <ul>
 *   <li>A shared active code with a declared source: approval creates a private pure variant that
 *       references the shared ISO/category rows; nothing is copied.
 *   <li>A shared active code without a source: redundant ({@code FIBER_REQUEST_DUPLICATE_CATALOG});
 *       a pending request from before publication is fulfilled by the shared canonical fibre on
 *       approval, without a private row.
 *   <li>A code absent from the catalogue: stays PENDING; approval returns {@code
 *       FIBER_CATALOG_PUBLICATION_REQUIRED} until a platform catalogue release publishes the code
 *       and its canonical pure fibre. Approval never mints an ISO code from user text.
 *   <li>An inactive code, a fibre-type mismatch or an incomplete publication: a named conflict.
 * </ul>
 *
 * <p>Notifications: submit → platform admins (IN_APP); approve/reject → requesting tenant (BOTH).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberRequestService {

  private static final int MIN_REVIEW_NOTE_LENGTH = 10;
  private static final List<FiberRequestStatus> OPEN_REQUEST_STATUSES =
      List.of(FiberRequestStatus.PENDING, FiberRequestStatus.APPROVED);

  private final FiberRequestRepository fiberRequestRepository;
  private final FiberReferenceQueryService referenceQueryService;
  private final FiberRepository fiberRepository;
  private final FiberSourceVariantService sourceVariantService;
  private final InAppNotificationService notificationService;
  private final TenantQueryPort tenantQueryPort;
  private final TenantSessionBinder tenantSessionBinder;

  /**
   * Submit a fiber request (tenant → platform). The open-request key is {@code (tenantId, isoCode,
   * materialSource)}; codes are normalised with trim + {@code Locale.ROOT} uppercase.
   */
  @Transactional
  public FiberRequestDto submit(CreateFiberRequestRequest request, UUID tenantId, UUID userId) {
    String isoCode = FiberReferenceQueryService.normalizeIsoCode(request.getIsoCode());
    String fiberType = FiberReferenceQueryService.normalizeIsoCode(request.getFiberType());
    MaterialSource materialSource = request.getMaterialSource();

    if (fiberRequestRepository.existsActiveLogicalDuplicate(
        tenantId, isoCode, materialSource, OPEN_REQUEST_STATUSES)) {
      throw duplicateRequest(isoCode, materialSource);
    }
    requireSharedCategory(fiberType);

    Optional<FiberIsoCode> shared = referenceQueryService.findIsoCode(isoCode);
    if (shared.isPresent()) {
      FiberIsoCode existing = shared.get();
      requireActiveMatchingCode(existing, fiberType);
      if (materialSource == null) {
        throw new FiberDomainException(
            "This ISO code is already in the shared catalogue",
            "FIBER_REQUEST_DUPLICATE_CATALOG",
            409,
            new Object[] {existing.getIsoCode()});
      }
      if (sourceVariantService
          .findActiveVariant(tenantId, existing.getId(), materialSource)
          .isPresent()) {
        throw FiberSourceVariantService.variantExists(existing, materialSource);
      }
    }

    FiberRequest entity =
        FiberRequest.builder()
            .requestedBy(userId)
            .isoCode(isoCode)
            .fiberName(request.getFiberName().trim())
            .fiberType(fiberType)
            .materialSource(materialSource)
            .description(request.getDescription() != null ? request.getDescription().trim() : null)
            .status(FiberRequestStatus.PENDING)
            .build();

    FiberRequest saved;
    try {
      saved = fiberRequestRepository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException exception) {
      throw duplicateRequest(isoCode, materialSource);
    }
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
   * Approve a fiber request (platform only). The request row is locked first, so one request has
   * exactly one outcome; every failure leaves it PENDING and rolls back all rows and effects.
   */
  @Transactional
  public FiberRequestDto approve(UUID requestId, UUID reviewedBy) {
    FiberRequest request =
        fiberRequestRepository
            .findByIdForUpdate(requestId)
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

    FiberSourceVariantService.SharedPureReference reference = resolvePublishedCode(request);
    return approveInRequestTenant(request, reference, reviewedBy);
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
            .findByIdForUpdate(requestId)
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
        tenantQueryPort.findAllByIds(tenantIds).stream()
            .collect(Collectors.toMap(TenantReference::id, TenantReference::name));

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
                  tenantQueryPort
                      .findById(e.getTenantId())
                      .map(TenantReference::name)
                      .orElse(e.getTenantId().toString());
              return FiberRequestDto.from(e, tenantName);
            });
  }

  /** Runs all approval writes with Java and PostgreSQL bound to the requesting tenant. */
  private FiberRequestDto approveInRequestTenant(
      FiberRequest request,
      FiberSourceVariantService.SharedPureReference reference,
      UUID reviewedBy) {
    TenantReference tenant =
        tenantQueryPort
            .findById(request.getTenantId())
            .orElseThrow(
                () ->
                    new FiberDomainException(
                        "Request tenant not found: " + request.getTenantId(),
                        "FIBER_REQUEST_TENANT_NOT_FOUND",
                        404));
    TenantContext.TenantSnapshot previous = TenantContext.capture();
    boolean completed = false;

    try {
      TenantContext.restore(
          new TenantContext.TenantSnapshot(request.getTenantId(), tenant.uid(), reviewedBy, null));
      tenantSessionBinder.bindToCurrentSession(request.getTenantId());

      if (request.getMaterialSource() != null) {
        sourceVariantService.createPrivateVariant(
            request.getTenantId(), reference, request.getFiberName(), request.getMaterialSource());
      } else {
        log.info(
            "Fiber request {} fulfilled by the shared canonical fibre for {}; no private row",
            request.getId(),
            reference.isoCode().getIsoCode());
      }

      request.setStatus(FiberRequestStatus.APPROVED);
      request.setReviewedBy(reviewedBy);
      request.setReviewNote(null);
      FiberRequest saved = fiberRequestRepository.saveAndFlush(request);

      sendOnApproveNotification(
          saved.getId(), saved.getTenantId(), saved.getIsoCode(), saved.getFiberName());
      // Flush any notification side effects while both the Java and PostgreSQL tenant contexts
      // still point at the request tenant.
      fiberRequestRepository.flush();

      log.info("Fiber request approved: id={}, isoCode={}", saved.getId(), saved.getIsoCode());
      FiberRequestDto result = FiberRequestDto.from(saved);
      completed = true;
      return result;
    } finally {
      TenantContext.restore(previous);
      // Do not issue another SQL statement after a failed flush: PostgreSQL has already marked
      // that transaction aborted and rebinding would mask the business-facing 409.
      if (completed && previous.tenantId() != null) {
        tenantSessionBinder.bindToCurrentSession(previous.tenantId());
      }
    }
  }

  /**
   * Re-reads the shared catalogue at approval time. The code, its category and its canonical pure
   * fibre must all be published and active; otherwise the request stays PENDING.
   */
  private FiberSourceVariantService.SharedPureReference resolvePublishedCode(FiberRequest request) {
    FiberIsoCode isoCode =
        referenceQueryService
            .findIsoCode(request.getIsoCode())
            .orElseThrow(
                () ->
                    new FiberDomainException(
                        "The ISO code is not in the shared catalogue yet; a platform catalogue"
                            + " release must publish it before this request can be approved",
                        "FIBER_CATALOG_PUBLICATION_REQUIRED",
                        409,
                        new Object[] {request.getIsoCode()}));
    requireActiveMatchingCode(isoCode, request.getFiberType());
    FiberCategory category =
        referenceQueryService
            .findCategory(isoCode.getFiberType())
            .filter(found -> Boolean.TRUE.equals(found.getIsActive()))
            .orElseThrow(() -> publicationIncomplete(isoCode));
    if (fiberRepository
        .findCanonicalByIsoCode(FiberCatalog.OWNER_ID, isoCode.getIsoCode())
        .isEmpty()) {
      throw publicationIncomplete(isoCode);
    }
    return new FiberSourceVariantService.SharedPureReference(isoCode, category);
  }

  private void requireActiveMatchingCode(FiberIsoCode isoCode, String requestedFiberType) {
    if (!Boolean.TRUE.equals(isoCode.getIsActive())) {
      throw new FiberDomainException(
          "The shared ISO code is inactive",
          "FIBER_CATALOG_CODE_INACTIVE",
          409,
          new Object[] {isoCode.getIsoCode()});
    }
    if (isoCode.getFiberType() == null
        || !isoCode.getFiberType().equalsIgnoreCase(requestedFiberType)) {
      throw new FiberDomainException(
          "Fiber type does not match the existing ISO code",
          "FIBER_REQUEST_FIBER_TYPE_MISMATCH",
          409,
          new Object[] {requestedFiberType, isoCode.getFiberType()});
    }
  }

  private void requireSharedCategory(String categoryCode) {
    if (referenceQueryService
        .findCategory(categoryCode)
        .filter(found -> Boolean.TRUE.equals(found.getIsActive()))
        .isEmpty()) {
      throw new FiberDomainException(
          "Fiber category not found: " + categoryCode,
          "FIBER_CATEGORY_NOT_FOUND",
          404,
          new Object[] {categoryCode});
    }
  }

  private static FiberDomainException publicationIncomplete(FiberIsoCode isoCode) {
    return new FiberDomainException(
        "The shared catalogue publication of this ISO code is incomplete",
        "FIBER_CATALOG_PUBLICATION_INCOMPLETE",
        409,
        new Object[] {isoCode.getIsoCode()});
  }

  private FiberDomainException duplicateRequest(String isoCode, MaterialSource materialSource) {
    return new FiberDomainException(
        "A fiber request with this ISO code and material source already exists",
        "FIBER_REQUEST_DUPLICATE_PENDING",
        409,
        new Object[] {isoCode, materialSource});
  }

  private void sendOnSubmitNotification(
      UUID fiberRequestId, UUID tenantId, String isoCode, String fiberName) {
    String tenantName =
        tenantQueryPort.findById(tenantId).map(TenantReference::name).orElse(tenantId.toString());
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
