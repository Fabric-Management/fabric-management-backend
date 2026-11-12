package com.fabricmanagement.notification.hub.domain;

/**
 * The groups the notification preferences screen shows. A user decides per group whether a
 * notification is also sent by e-mail; the group's name and description are the frontend's wording
 * for this code.
 */
public enum NotificationGroup {
  APPROVALS,
  SALES,
  PRODUCTION,
  QUALITY,
  PROCUREMENT,
  FINANCE,
  INVENTORY,
  SYSTEM
}
