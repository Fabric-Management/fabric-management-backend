package com.fabricmanagement.sales.sample.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.sample.domain.DeliveryMethod;
import com.fabricmanagement.sales.sample.domain.SampleDelivery;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
import com.fabricmanagement.sales.sample.domain.SampleRequestStatus;
import com.fabricmanagement.sales.sample.infra.repository.SampleDeliveryRepository;
import com.fabricmanagement.sales.sample.infra.repository.SampleRequestRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SampleManagementService {

  private final SampleRequestRepository requestRepository;
  private final SampleDeliveryRepository deliveryRepository;

  @Transactional
  public SampleRequest requestSample(SampleRequest request) {
    request.setTenantId(TenantContext.getCurrentTenantId());
    request.setStatus(SampleRequestStatus.REQUESTED);
    return requestRepository.save(request);
  }

  @Transactional
  public SampleDelivery dispatchSample(
      UUID requestId,
      DeliveryMethod method,
      String trackingNumber,
      String cargoCompany,
      UUID deliveredById) {

    SampleRequest request = getActiveRequest(requestId);

    if (request.getStatus() != SampleRequestStatus.REQUESTED
        && request.getStatus() != SampleRequestStatus.PREPARING) {
      throw new SalesDomainException(
          "Sample is already dispatched or in an invalid state.",
          "SALES_SAMPLE_INVALID_STATE",
          HttpStatus.BAD_REQUEST);
    }

    request.setStatus(SampleRequestStatus.DISPATCHED);
    request.setDeliveryMethod(method);
    requestRepository.save(request);

    SampleDelivery delivery = new SampleDelivery();
    delivery.setTenantId(request.getTenantId());
    delivery.setSampleRequestId(requestId);
    delivery.setDeliveryMethod(method);
    delivery.setTrackingNumber(trackingNumber);
    delivery.setCargoCompany(cargoCompany);
    delivery.setDeliveredById(deliveredById);
    delivery.setDispatchedAt(Instant.now());

    // Note: Emit SampleDispatchedEvent so IWM module can create a SAMPLE_OUT StockTransaction

    return deliveryRepository.save(delivery);
  }

  @Transactional
  public SampleDelivery markAsDelivered(UUID deliveryId, String recipientName, String photoUrl) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // Fix #6: tenant-isolated delivery lookup
    SampleDelivery delivery =
        deliveryRepository
            .findByTenantIdAndIdAndIsActiveTrue(tenantId, deliveryId)
            .orElseThrow(() -> new NotFoundException("Sample delivery not found: " + deliveryId));

    delivery.setDeliveredAt(Instant.now());
    delivery.setRecipientName(recipientName);
    delivery.setDeliveryPhoto(photoUrl);
    deliveryRepository.save(delivery);

    SampleRequest request = getActiveRequest(delivery.getSampleRequestId());
    request.setStatus(SampleRequestStatus.DELIVERED);
    requestRepository.save(request);

    return delivery;
  }

  private SampleRequest getActiveRequest(UUID requestId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return requestRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, requestId)
        .orElseThrow(() -> new NotFoundException("Sample request not found: " + requestId));
  }
}
