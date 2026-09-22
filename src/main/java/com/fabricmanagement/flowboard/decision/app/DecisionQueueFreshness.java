package com.fabricmanagement.flowboard.decision.app;

import com.fabricmanagement.flowboard.decision.app.listener.DecisionFollowEventListener;
import com.fabricmanagement.flowboard.decision.app.listener.DecisionProjectionListener;
import com.fabricmanagement.flowboard.task.app.OrderCoverCaseOpenedListener;
import java.util.Set;

/** Listener outputs whose delayed publication can make decision queue results incomplete. */
public final class DecisionQueueFreshness {
  public static final Set<String> LISTENERS =
      Set.of(
          DecisionProjectionListener.class.getName(),
          DecisionFollowEventListener.class.getName(),
          OrderCoverCaseOpenedListener.class.getName());

  private DecisionQueueFreshness() {}
}
