# NotificationHub — Merkezi Bildirim Sistemi

> Modül: Bildirim & i18n (08-notification-i18n) | Versiyon: 1.0 | Son güncelleme: 2026-03-17
> Kanonik kaynak: NotificationTemplate, NotificationQueue, NotificationLog, UserNotificationPreference burada tanımlanır.

## Genel Bakış

Tüm modüller NotificationHub'a event gönderir. Hub şablonu bulur, dili belirler, kanalı seçer, gönderir. Tek şablon sistemi, tek kanal yönetimi, tek log. FlowBoard'daki notification_preference kaldırılmıştır.

## 3 Kanal: IN_APP (WebSocket), EMAIL (anlık/toplu), PUSH (mobil/masaüstü)

## 47 Event — tam liste: 11-cross-cutting/event-catalog.md

## Entity'ler

**NotificationTemplate** (`notification.notification_template`): eventType, channel, titleKey→TranslationKey, bodyKey→TranslationKey, importance (NORMAL/HIGH/CRITICAL), deliveryType (INSTANT/SCHEDULED/DIGEST), groupingWindowMinutes, isActive, tenantId.

**NotificationQueue** (`notification.notification_queue`): recipientId, eventType, channel, importance, deliveryType, scheduledAt, payload JSONB, status (PENDING/PROCESSING/SENT/FAILED), retryCount (max 3), lastError, locale.

**NotificationLog** (`notification.notification_log`): recipientId, eventType, channel, importance, title, body, locale, sentAt, isRead, readAt, isClicked, clickedAt, actionTaken (APPROVED/REJECTED/DISMISSED), actionTakenAt, groupId.

**UserNotificationPreference** (`notification.user_notification_preference`): userId, eventType, inApp (true), email (true), push (true). CRITICAL olaylar → tercih yok sayılır.

## Gönderim Kuralları

CRITICAL → Anında, tüm kanallar, tercih yok sayılır. HIGH → 5 dk gruplama. NORMAL → 5 dk gruplama, kullanıcı tercihi.

## Akış: Event → Template → Tercih kontrolü → Locale → Render → Queue → Kanal servisi → Log → Etkileşim takibi
