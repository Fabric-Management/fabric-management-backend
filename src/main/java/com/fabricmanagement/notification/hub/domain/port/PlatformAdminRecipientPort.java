package com.fabricmanagement.notification.hub.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Resolves the platform administrators: the active users of the platform's own (system) tenant who
 * hold the platform administrator role. Platform-level notifications go to them and to no tenant
 * user (NOTIF-UNIFY-1 B4).
 *
 * <p>Call it inside the system tenant's context; the users table is row-level secured.
 */
public interface PlatformAdminRecipientPort {

  List<UUID> findPlatformAdministrators();
}
