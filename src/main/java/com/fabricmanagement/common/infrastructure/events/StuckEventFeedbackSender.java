package com.fabricmanagement.common.infrastructure.events;

public interface StuckEventFeedbackSender {

  void sendOpsReport(FollowUpFeedbackReport report);
}
