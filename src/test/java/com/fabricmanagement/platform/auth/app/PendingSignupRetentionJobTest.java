package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.infra.repository.PendingSignupRepository;
import com.fabricmanagement.platform.auth.infra.repository.SignupLinkPublicationRepository;
import com.fabricmanagement.platform.communication.app.EmailOutboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PendingSignupRetentionJobTest {

  private static final Instant NOW = Instant.parse("2026-11-14T03:40:00Z");

  @Mock private PendingSignupRepository pendingSignups;
  @Mock private SignupLinkPublicationRepository linkPublications;
  @Mock private EmailOutboxService emailOutbox;

  private PendingSignupRetentionJob job;

  @BeforeEach
  void setUp() {
    job =
        new PendingSignupRetentionJob(
            pendingSignups, linkPublications, emailOutbox, Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(job, "enabled", true);
    ReflectionTestUtils.setField(job, "expiredDays", 7);
    ReflectionTestUtils.setField(job, "completedDays", 30);
    ReflectionTestUtils.setField(job, "linkMinutesAfterSending", 10);
  }

  @Test
  @DisplayName("removes signups expired a week ago and completed signups older than a month")
  void purgesExpiredAndOldCompletedSignups() {
    when(pendingSignups.deleteExpiredBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);
    when(pendingSignups.deleteCompletedBefore(NOW.minus(Duration.ofDays(30)))).thenReturn(2);

    job.scheduledPurge();

    verify(pendingSignups).deleteExpiredBefore(NOW.minus(Duration.ofDays(7)));
    verify(pendingSignups).deleteCompletedBefore(NOW.minus(Duration.ofDays(30)));
  }

  @Test
  @DisplayName("removes raw links from publications and from sent mails once they are spent")
  void removesSpentLinks() {
    Instant unusable = NOW.minus(Duration.ofHours(25));
    when(linkPublications.deleteDelivered()).thenReturn(3);
    when(linkPublications.deleteUndeliveredBefore(unusable)).thenReturn(1);
    when(emailOutbox.redactLinks(
            TenantContext.SYSTEM_TENANT_ID,
            PendingSignupRetentionJob.SIGNUP_LINK_PATTERN,
            PendingSignupRetentionJob.SIGNUP_LINK_REPLACEMENT,
            10,
            25 * 60,
            7 * 24 * 60))
        .thenReturn(5);

    var counts = job.removeSpentLinks(NOW);

    assertThat(counts.publications()).isEqualTo(4);
    assertThat(counts.mails()).isEqualTo(5);
  }

  @Test
  @DisplayName("does nothing while switched off")
  void doesNothingWhenDisabled() {
    ReflectionTestUtils.setField(job, "enabled", false);

    job.scheduledPurge();
    job.scheduledLinkRemoval();

    verifyNoInteractions(pendingSignups, linkPublications, emailOutbox);
  }

  @Test
  @DisplayName("the link pattern matches a signup token and nothing shorter, such as a UUID token")
  void linkPatternMatchesOnlySignupTokens() {
    // Java and PostgreSQL agree on this subset of regular expressions.
    Pattern link = Pattern.compile(PendingSignupRetentionJob.SIGNUP_LINK_PATTERN);
    String signup = "https://app.example/setup?token=q3Z8mV1xN0bH7cK2tW9yR4sL6pD5fG1jA8uE3iO0nTk";
    String invitation = "https://app.example/setup?token=5b0f3c1e-8a5d-4f0e-9d0a-2f6f4a1f7c11";
    String longer = "https://app.example/setup?token=q3Z8mV1xN0bH7cK2tW9yR4sL6pD5fG1jA8uE3iO0nTkX";

    assertThat(link.matcher(signup).find()).isTrue();
    assertThat(link.matcher(signup).replaceAll("$1removed"))
        .isEqualTo("https://app.example/setup?token=removed");
    assertThat(link.matcher(invitation).find()).isFalse();
    assertThat(link.matcher(longer).find()).isFalse();
    assertThat(link.matcher(signup + "\">").find()).isTrue();
  }

  @Test
  @DisplayName("a link counts as spent its lifetime plus an hour after it was issued")
  void unusableWindowFollowsTheLinkLifetime() {
    assertThat(PendingSignupRetentionJob.LINK_UNUSABLE_AFTER)
        .isEqualTo(SignupRequestService.LINK_LIFETIME.plusHours(1));
  }
}
