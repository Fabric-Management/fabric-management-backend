package com.fabricmanagement.common.infrastructure.events;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlagWritingStuckEventHandler implements StuckEventHandler {

  private final IncompleteFollowUpFlagRepository flagRepository;
  private final List<StuckEventPresenter> presenters;
  private final ObjectMapper objectMapper;
  private final TenantSessionBinder tenantSessionBinder;
  private final ObjectProvider<FollowUpResolutionNotifier> notifierProvider;

  @Override
  @Transactional
  public void onNewlyStuck(StuckEventContext context) {
    if (context.tenantId() == null) {
      log.warn("Skipping follow-up flag without tenant: publicationId={}", context.publicationId());
      return;
    }

    TenantContext.executeInTenantContext(
        context.tenantId(),
        () -> {
          tenantSessionBinder.bindToCurrentSession(context.tenantId());
          StuckEventPresentation presentation =
              presenterFor(context.eventType())
                  .present(context.tenantId(), readPayload(context.payload()));
          flagRepository
              .findByTenantIdAndPublicationId(context.tenantId(), context.publicationId())
              .ifPresentOrElse(
                  existing -> {
                    if (existing.getStatus() != FollowUpFlagStatus.ACTIVE) {
                      existing.raiseAgain(presentation);
                      flagRepository.save(existing);
                    }
                  },
                  () ->
                      flagRepository.save(
                          IncompleteFollowUpFlag.raise(
                              context.tenantId(),
                              context.publicationId(),
                              context.eventType(),
                              presentation)));
        });
  }

  @Override
  @Transactional
  public void onResolved(StuckEventContext context) {
    if (context.tenantId() == null) {
      log.warn(
          "Skipping follow-up flag resolution without tenant: publicationId={}",
          context.publicationId());
      return;
    }

    TenantContext.executeInTenantContext(
        context.tenantId(),
        () -> {
          tenantSessionBinder.bindToCurrentSession(context.tenantId());
          flagRepository
              .findByTenantIdAndPublicationId(context.tenantId(), context.publicationId())
              .filter(flag -> flag.getStatus() == FollowUpFlagStatus.ACTIVE)
              .ifPresent(
                  flag -> {
                    flag.resolve(Instant.now());
                    flagRepository.save(flag);
                    notifyResolved(flag);
                  });
        });
  }

  private void notifyResolved(IncompleteFollowUpFlag flag) {
    ResolvedFollowUp resolvedFollowUp =
        new ResolvedFollowUp(
            flag.getTenantId(),
            flag.getAffectedUserId(),
            flag.getEntityType(),
            flag.getEntityRef(),
            flag.getReferenceId(),
            flag.getReferenceType(),
            flag.getSummary());
    try {
      notifierProvider.ifAvailable(notifier -> notifier.notifyResolved(resolvedFollowUp));
    } catch (Exception exception) {
      log.warn(
          "Failed to notify resolved follow-up: publicationId={}",
          flag.getPublicationId(),
          exception);
    }
  }

  private StuckEventPresenter presenterFor(String eventType) {
    return presenters.stream()
        .filter(presenter -> presenter.supports(eventType))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No stuck-event presenter registered"));
  }

  private JsonNode readPayload(String payload) {
    try {
      return objectMapper.readTree(payload);
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      return objectMapper.createObjectNode();
    }
  }
}
