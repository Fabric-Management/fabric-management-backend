package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import com.fabricmanagement.platform.communication.dto.NotificationRequest;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberRequest;
import com.fabricmanagement.product.fiber.domain.FiberRequestStatus;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequestRequest;
import com.fabricmanagement.product.fiber.dto.FiberRequestDto;
import com.fabricmanagement.product.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRequestRepository;
import java.util.List;
import java.util.Locale;
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
  private static final List<FiberRequestStatus> OPEN_REQUEST_STATUSES =
      List.of(FiberRequestStatus.PENDING, FiberRequestStatus.APPROVED);

  private final FiberRequestRepository fiberRequestRepository;
  private final FiberIsoCodeRepository fiberIsoCodeRepository;
  private final FiberCategoryRepository fiberCategoryRepository;
  private final ProductRepository productRepository;
  private final FiberRepository fiberRepository;
  private final InAppNotificationService notificationService;
  private final TenantQueryPort tenantQueryPort;
  private final TenantSessionBinder tenantSessionBinder;

  /**
   * Submit a fiber request (tenant → platform).
   *
   * <p>The open-request key is {@code (tenantId, isoCode, materialSource)}. An existing
   * tenant-owned ISO row may be reused only for a declared source variant; a template-only row is
   * reported as missing tenant reference data, while a code absent from both scopes remains a
   * genuinely new-code request.
   *
   * @param request Create request
   * @param tenantId Current tenant ID
   * @param userId Requesting user ID
   * @return Created fiber request
   */
  @Transactional
  public FiberRequestDto submit(CreateFiberRequestRequest request, UUID tenantId, UUID userId) {
    String isoCode = request.getIsoCode().trim().toUpperCase(Locale.ROOT);
    String fiberType = request.getFiberType().trim().toUpperCase(Locale.ROOT);
    MaterialSource materialSource = request.getMaterialSource();

    if (fiberRequestRepository.existsActiveLogicalDuplicate(
        tenantId, isoCode, materialSource, OPEN_REQUEST_STATUSES)) {
      throw duplicateRequest(isoCode, materialSource);
    }

    IsoResolution isoResolution = classifyIsoCode(tenantId, isoCode);
    if (isoResolution.state() == IsoResolutionState.TEMPLATE_ONLY) {
      throw missingTenantReference("prod_fiber_iso_code", isoCode);
    }
    if (isoResolution.state() == IsoResolutionState.TENANT) {
      validateExistingCodeRequest(tenantId, isoResolution.isoCode(), fiberType, materialSource);
    }
    resolveTenantCategory(tenantId, fiberType);

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
   * Approve a fiber request (platform only).
   *
   * <p>Resolves or creates the ISO row and creates Product/Fiber in the requesting tenant's
   * context. Material source is a declaration, not certification evidence.
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

    return approveInRequestTenant(request, reviewedBy);
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
  private FiberRequestDto approveInRequestTenant(FiberRequest request, UUID reviewedBy) {
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

      createFiberFromRequest(request);

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

  private void createFiberFromRequest(FiberRequest request) {
    FiberIsoCode isoCode = resolveIsoCodeForApproval(request);
    FiberCategory category = resolveTenantCategory(request.getTenantId(), request.getFiberType());

    Product product = productRepository.save(Product.create(ProductType.FIBER, "KG"));
    Fiber fiber =
        Fiber.createPureFiber(
            product, category, isoCode, request.getFiberName(), request.getMaterialSource());
    try {
      fiberRepository.saveAndFlush(fiber);
    } catch (DataIntegrityViolationException exception) {
      throw new FiberDomainException(
          "A fiber variant with this ISO code and material source already exists",
          "FIBER_REQUEST_VARIANT_EXISTS",
          409,
          new Object[] {request.getIsoCode(), request.getMaterialSource()});
    }

    log.info(
        "Created fiber from request: isoCode={}, productId={}, fiberId={}, tenantId={}",
        request.getIsoCode(),
        product.getId(),
        fiber.getId(),
        request.getTenantId());
  }

  private FiberIsoCode resolveIsoCodeForApproval(FiberRequest request) {
    IsoResolution initial = classifyIsoCode(request.getTenantId(), request.getIsoCode());
    if (initial.state() == IsoResolutionState.TEMPLATE_ONLY) {
      throw missingTenantReference("prod_fiber_iso_code", request.getIsoCode());
    }
    if (initial.state() == IsoResolutionState.TENANT) {
      validateExistingCodeRequest(
          request.getTenantId(),
          initial.isoCode(),
          request.getFiberType(),
          request.getMaterialSource());
      return initial.isoCode();
    }

    fiberIsoCodeRepository.acquireCreationLock(request.getTenantId(), request.getIsoCode());
    IsoResolution locked = classifyIsoCode(request.getTenantId(), request.getIsoCode());
    if (locked.state() == IsoResolutionState.TEMPLATE_ONLY) {
      throw missingTenantReference("prod_fiber_iso_code", request.getIsoCode());
    }
    if (locked.state() == IsoResolutionState.TENANT) {
      validateExistingCodeRequest(
          request.getTenantId(),
          locked.isoCode(),
          request.getFiberType(),
          request.getMaterialSource());
      return locked.isoCode();
    }

    FiberIsoCode created =
        FiberIsoCode.builder()
            .isoCode(request.getIsoCode())
            .fiberName(request.getFiberName())
            .fiberType(request.getFiberType())
            .description(request.getDescription())
            .isOfficialIso(false)
            .build();
    return fiberIsoCodeRepository.saveAndFlush(created);
  }

  private IsoResolution classifyIsoCode(UUID tenantId, String isoCode) {
    Optional<FiberIsoCode> tenantRow =
        fiberIsoCodeRepository.findByTenantIdAndIsoCodeIgnoreCase(tenantId, isoCode);
    if (tenantRow.isPresent()) {
      return new IsoResolution(IsoResolutionState.TENANT, tenantRow.get());
    }
    boolean templateExists =
        fiberIsoCodeRepository
            .findByTenantIdAndIsoCodeIgnoreCase(TenantContext.TEMPLATE_TENANT_ID, isoCode)
            .isPresent();
    return templateExists
        ? new IsoResolution(IsoResolutionState.TEMPLATE_ONLY, null)
        : new IsoResolution(IsoResolutionState.NEW, null);
  }

  private FiberCategory resolveTenantCategory(UUID tenantId, String categoryCode) {
    Optional<FiberCategory> tenantRow =
        fiberCategoryRepository.findByTenantIdAndCategoryCode(tenantId, categoryCode);
    if (tenantRow.isPresent()) {
      return tenantRow.get();
    }
    if (fiberCategoryRepository
        .findByTenantIdAndCategoryCode(TenantContext.TEMPLATE_TENANT_ID, categoryCode)
        .isPresent()) {
      throw missingTenantReference("prod_fiber_category", categoryCode);
    }
    throw new FiberDomainException(
        "Fiber category not found: " + categoryCode,
        "FIBER_CATEGORY_NOT_FOUND",
        404,
        new Object[] {categoryCode});
  }

  private void validateExistingCodeRequest(
      UUID tenantId,
      FiberIsoCode isoCode,
      String requestedFiberType,
      MaterialSource materialSource) {
    if (materialSource == null) {
      throw new FiberDomainException(
          "Material source is required for a variant of an existing ISO code",
          "FIBER_REQUEST_MATERIAL_SOURCE_REQUIRED",
          400,
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
    if (fiberRepository.existsByTenantIdAndFiberIsoCode_IdAndMaterialSourceAndIsActiveTrue(
        tenantId, isoCode.getId(), materialSource)) {
      throw new FiberDomainException(
          "A fiber variant with this ISO code and material source already exists",
          "FIBER_REQUEST_VARIANT_EXISTS",
          409,
          new Object[] {isoCode.getIsoCode(), materialSource});
    }
  }

  private FiberDomainException missingTenantReference(String table, String code) {
    return new FiberDomainException(
        "Tenant reference data is missing for " + table + ": " + code,
        "FIBER_TENANT_REFERENCE_DATA_MISSING",
        409,
        new Object[] {table, code});
  }

  private FiberDomainException duplicateRequest(String isoCode, MaterialSource materialSource) {
    return new FiberDomainException(
        "A fiber request with this ISO code and material source already exists",
        "FIBER_REQUEST_DUPLICATE_PENDING",
        409,
        new Object[] {isoCode, materialSource});
  }

  private enum IsoResolutionState {
    TENANT,
    TEMPLATE_ONLY,
    NEW
  }

  private record IsoResolution(IsoResolutionState state, FiberIsoCode isoCode) {}

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
