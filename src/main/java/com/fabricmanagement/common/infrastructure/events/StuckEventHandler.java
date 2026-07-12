package com.fabricmanagement.common.infrastructure.events;

public interface StuckEventHandler {

  void onNewlyStuck(StuckEventContext context);

  void onResolved(StuckEventContext context);
}
