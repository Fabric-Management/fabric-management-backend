package com.fabricmanagement.flowboard.automation.domain;

import java.util.UUID;
import lombok.Getter;

/**
 * AutomationEngine'in bir kuralı değerlendirirken kullandığı bağlam.
 *
 * <p>Sonsuz döngü koruması için {@code depth} takip edilir — maksimum 3 derinlik.
 */
@Getter
public class AutomationContext {

  private static final int MAX_DEPTH = 3;

  private final UUID taskId;
  private final UUID boardId;
  private final int depth;

  /** Başlangıç context'i — ilk tetikleme. */
  public static AutomationContext initial(UUID taskId, UUID boardId) {
    return new AutomationContext(taskId, boardId, 0);
  }

  private AutomationContext(UUID taskId, UUID boardId, int depth) {
    this.taskId = taskId;
    this.boardId = boardId;
    this.depth = depth;
  }

  /** Bir derin bağlam döner — aksiyon başka bir kuralı tetiklerken kullanılır. */
  public AutomationContext deeper() {
    return new AutomationContext(taskId, boardId, depth + 1);
  }

  /** Maksimum derinliğe ulaşıldı mı? */
  public boolean isDepthExceeded() {
    return depth >= MAX_DEPTH;
  }
}
