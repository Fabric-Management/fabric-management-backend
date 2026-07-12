package com.fabricmanagement.common.infrastructure.events;

public interface FollowUpResolutionNotifier {

  void notifyResolved(ResolvedFollowUp event);
}
