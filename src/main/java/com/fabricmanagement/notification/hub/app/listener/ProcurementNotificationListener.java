package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.procurement.purchaseorder.domain.event.PoConfirmedEvent;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoDeliveryLateEvent;
import com.fabricmanagement.procurement.purchaseorder.domain.event.PoPartiallyReceivedEvent;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteReceivedEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqDeadlineApproachingEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqNoResponseEvent;
import com.fabricmanagement.procurement.rfq.domain.event.RfqSentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tedarik zinciri event listener'ları.
 *
 * <p>NOT: Alıcı listesi (tedarik sorumlusu userId) FlowBoard departman modülü entegrasyonundan
 * sonra doldurulacak. Şimdilik log seviyesinde takip yapılır.
 *
 * <p>TODO (FlowBoard entegrasyonu sonrası): - RfqSent → Tedarik departmanı sorumlusuna NORMAL
 * bildirim - SupplierQuoteReceived→ Talep sahibine NORMAL bildirim - RfqDeadlineApproach → Tedarik
 * sorumlusuna HIGH bildirim - RfqNoResponse → Tedarik sorumlusuna HIGH bildirim - PoConfirmed →
 * Tedarik ve muhasebe ekibine NORMAL bildirim - PoPartiallyReceived → Depo sorumlusuna NORMAL
 * bildirim - PoDeliveryLate → Tedarik müdürüne HIGH bildirim
 */
@Component
@Slf4j
public class ProcurementNotificationListener {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onRfqSent(RfqSentEvent event) {
    log.info(
        "NotificationHub ← RfqSent: rfq={} supplierCount={}",
        event.getRfqNumber(),
        event.getSupplierIds().size());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onSupplierQuoteReceived(SupplierQuoteReceivedEvent event) {
    log.info("NotificationHub ← SupplierQuoteReceived: supplier={}", event.getSupplierName());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onRfqDeadlineApproaching(RfqDeadlineApproachingEvent event) {
    log.warn(
        "NotificationHub ← RfqDeadlineApproaching: rfq={} hoursLeft={}",
        event.getRfqNumber(),
        event.getHoursRemaining());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onRfqNoResponse(RfqNoResponseEvent event) {
    log.warn(
        "NotificationHub ← RfqNoResponse: rfq={} supplier={}",
        event.getRfqNumber(),
        event.getSupplierName());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onPoConfirmed(PoConfirmedEvent event) {
    log.info("NotificationHub ← PoConfirmed: po={}", event.getPoNumber());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onPoPartiallyReceived(PoPartiallyReceivedEvent event) {
    log.info(
        "NotificationHub ← PoPartiallyReceived: po={} received={}/{}",
        event.getPoNumber(),
        event.getReceivedItemCount(),
        event.getTotalItemCount());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onPoDeliveryLate(PoDeliveryLateEvent event) {
    log.warn(
        "NotificationHub ← PoDeliveryLate [HIGH]: po={} supplier={} lateDays={}",
        event.getPoNumber(),
        event.getSupplierName(),
        event.getLateDays());
  }
}
