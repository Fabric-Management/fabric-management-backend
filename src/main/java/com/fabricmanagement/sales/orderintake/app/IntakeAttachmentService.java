package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.IntakeAttachment;
import com.fabricmanagement.sales.orderintake.domain.IntakeAttachmentKind;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.IntakeAttachmentRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Files received from the customer, kept with the order (SOI IK-12, D7). */
@Service
@RequiredArgsConstructor
public class IntakeAttachmentService {

  private final OrderIntakeAccess access;
  private final IntakeAttachmentRepository repository;
  private final Clock clock;

  public record Download(String fileName, String contentType, byte[] content) {}

  @Transactional
  public CustomerRequestDtos.AttachmentDto upload(
      UUID orderId, IntakeAttachmentKind kind, MultipartFile file, UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    if (file == null || file.isEmpty()) {
      throw OrderIntakeException.rule("FILE_REQUIRED", "Choose a file to upload");
    }
    if (file.getSize() > IntakeAttachment.MAX_BYTES) {
      throw OrderIntakeException.rule("FILE_TOO_LARGE", "Files are limited to 10 MB");
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      throw OrderIntakeException.rule("FILE_UNREADABLE", "The uploaded file could not be read");
    }
    String type = file.getContentType();
    if (type == null
        || !IntakeAttachment.ALLOWED_TYPES.contains(type.toLowerCase(java.util.Locale.ROOT))) {
      throw OrderIntakeException.rule(
          "FILE_TYPE_NOT_ALLOWED", "Only images, PDF and plain text are accepted");
    }
    IntakeAttachment saved =
        repository.save(
            IntakeAttachment.store(
                order.getTradingPartnerId(),
                order.getId(),
                null,
                kind,
                file.getOriginalFilename(),
                type,
                content,
                sha256(content),
                actor,
                clock.instant()));
    return toDto(saved);
  }

  @Transactional(readOnly = true)
  public List<CustomerRequestDtos.AttachmentDto> forOrder(UUID orderId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    return repository.summariesForOrder(TenantContext.requireTenantId(), order.getId()).stream()
        .map(IntakeAttachmentService::summaryDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public Download downloadForOrder(UUID orderId, UUID attachmentId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    IntakeAttachment attachment = load(attachmentId);
    boolean ofThisOrder = order.getId().equals(attachment.getSalesOrderId());
    boolean unattachedOfCustomer =
        attachment.getSalesOrderId() == null
            && order.getTradingPartnerId().equals(attachment.getCustomerId());
    if (!ofThisOrder && !unattachedOfCustomer) {
      throw OrderIntakeException.notFound("Attachment", attachmentId);
    }
    return new Download(
        attachment.getFileName(), attachment.getContentType(), attachment.getContent());
  }

  @Transactional(readOnly = true)
  public Download downloadForRequest(UUID requestId, UUID attachmentId) {
    IntakeAttachment attachment = load(attachmentId);
    if (!requestId.equals(attachment.getRequestId())) {
      throw OrderIntakeException.notFound("Attachment", attachmentId);
    }
    return new Download(
        attachment.getFileName(), attachment.getContentType(), attachment.getContent());
  }

  /** Links uploaded files of the order to a request; files of other orders are refused. */
  void linkToRequest(
      UUID orderId, UUID customerId, UUID requestId, Collection<UUID> attachmentIds) {
    if (attachmentIds == null) {
      return;
    }
    for (UUID id : attachmentIds) {
      IntakeAttachment attachment = load(id);
      boolean sameCustomer = customerId.equals(attachment.getCustomerId());
      boolean sameOrder = orderId == null || orderId.equals(attachment.getSalesOrderId());
      if (!sameCustomer || !sameOrder) {
        throw OrderIntakeException.notFound("Attachment", id);
      }
      attachment.linkTo(requestId);
      repository.save(attachment);
    }
  }

  boolean requestHasFiles(UUID requestId) {
    return repository.existsByTenantIdAndRequestIdAndIsActiveTrue(
        TenantContext.requireTenantId(), requestId);
  }

  List<CustomerRequestDtos.AttachmentDto> forRequest(UUID requestId) {
    return repository.summariesForRequest(TenantContext.requireTenantId(), requestId).stream()
        .map(IntakeAttachmentService::summaryDto)
        .toList();
  }

  void requireAttachmentOfCustomer(UUID attachmentId, UUID customerId) {
    if (attachmentId == null) {
      return;
    }
    if (!customerId.equals(load(attachmentId).getCustomerId())) {
      throw OrderIntakeException.notFound("Attachment", attachmentId);
    }
  }

  private IntakeAttachment load(UUID attachmentId) {
    return repository
        .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), attachmentId)
        .orElseThrow(() -> OrderIntakeException.notFound("Attachment", attachmentId));
  }

  static CustomerRequestDtos.AttachmentDto toDto(IntakeAttachment value) {
    return new CustomerRequestDtos.AttachmentDto(
        value.getId(),
        value.getSalesOrderId(),
        value.getRequestId(),
        value.getKind(),
        value.getFileName(),
        value.getContentType(),
        value.getSizeBytes(),
        value.getUploadedBy(),
        value.getUploadedAt());
  }

  static CustomerRequestDtos.AttachmentDto summaryDto(IntakeAttachmentRepository.Summary value) {
    return new CustomerRequestDtos.AttachmentDto(
        value.getId(),
        value.getSalesOrderId(),
        value.getRequestId(),
        value.getKind(),
        value.getFileName(),
        value.getContentType(),
        value.getSizeBytes(),
        value.getUploadedBy(),
        value.getUploadedAt());
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
