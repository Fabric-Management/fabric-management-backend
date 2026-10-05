package com.fabricmanagement.sales.salesorder.app;

import static com.fabricmanagement.sales.salesorder.dto.SalesOrderCapabilityDto.NO_OBJECT_ACCESS;
import static com.fabricmanagement.sales.salesorder.dto.SalesOrderCapabilityDto.PERMISSION_DENIED;
import static com.fabricmanagement.sales.salesorder.dto.SalesOrderCapabilityDto.WRONG_STATUS;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderCapabilityDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderCapabilityDto.Action;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves what the current user may do with a sales order, with the same checks the commands
 * apply: object access, the endpoint's permission, then the order's status and flow stage. The
 * status rules are the entity's own ({@link OrderStatus}), so a command the capability allows is a
 * command the entity accepts.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderCapabilityService {

  private static final String RESOURCE = "sales";

  private final SalesOrderRepository orders;
  private final SalesOrderAccessPolicy accessPolicy;
  private final SpELPermissionEvaluator permissions;

  @Transactional(readOnly = true)
  public List<SalesOrderCapabilityDto> resolve(
      UUID orderId, UUID actor, Authentication authentication) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order =
        orders
            .findByTenantIdAndId(tenantId, orderId)
            .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
    return resolve(tenantId, actor, authentication, order);
  }

  List<SalesOrderCapabilityDto> resolve(
      UUID tenantId, UUID actor, Authentication authentication, SalesOrder order) {
    boolean objectAccess = accessPolicy.canWrite(tenantId, actor, order);
    return Arrays.stream(Action.values())
        .map(
            action ->
                SalesOrderCapabilityDto.of(
                    action, block(action, order, objectAccess, authentication)))
        .toList();
  }

  /** Why the action is not allowed now, or null. The first failing check names the reason. */
  private String block(
      Action action, SalesOrder order, boolean objectAccess, Authentication authentication) {
    if (!objectAccess) {
      return NO_OBJECT_ACCESS;
    }
    if (!permissions.can(authentication, RESOURCE, permissionAction(action))) {
      return PERMISSION_DENIED;
    }
    return statusBlock(action, order);
  }

  /** The permission the endpoint of this action is annotated with. */
  static String permissionAction(Action action) {
    return switch (action) {
      case SHIP -> "ship";
      case CANCEL -> "cancel";
      case DELETE -> "delete";
      case UPDATE, PROCESS, DELIVER, HOLD, RESUME, REVISE -> "write";
    };
  }

  private static String statusBlock(Action action, SalesOrder order) {
    OrderStatus status = order.getStatus();
    boolean statusAllows =
        switch (action) {
          case UPDATE -> status.canEdit();
          case DELETE -> status.canDelete();
          case PROCESS -> status.canStartProcessing();
          case SHIP -> status.canShip();
          case DELIVER -> status.canDeliver();
          case CANCEL -> status.canCancel();
          case HOLD -> status.canHold();
          case RESUME -> status.canResume();
          case REVISE -> status.canRevise();
        };
    if (!statusAllows) {
      return WRONG_STATUS;
    }
    // Only sales' draft is edited: with planning or the customer it must be withdrawn first.
    if (action == Action.UPDATE && order.getFlowStage() != OrderFlowStage.DRAFT) {
      return SalesOrder.WITH_PLANNING;
    }
    return null;
  }
}
