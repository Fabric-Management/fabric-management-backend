package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.tenant.EmailSandbox;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EMAIL-SANDBOX-1. The rule under test is one sentence: a sandboxed tenant cannot reach a third
 * party by email, and the prospect who created it receives everything instead.
 */
class EmailRecipientPolicyTest {

  private static final UUID SANDBOXED = UUID.randomUUID();
  private static final UUID SANDBOXED_WITHOUT_ADDRESS = UUID.randomUUID();
  private static final UUID REAL = UUID.randomUUID();
  private static final UUID UNKNOWN = UUID.randomUUID();

  private static final String PROSPECT = "prospect@acme.co.uk";
  private static final String STRANGER = "cfo@unsuspecting-mill.com";

  private final EmailRecipientPolicy policy = new EmailRecipientPolicy(stubPort());

  @Test
  @DisplayName("a sandboxed tenant's mail goes to the prospect, never to the stranger")
  void redirectsToProspect() {
    var resolution = policy.resolveFor(SANDBOXED, STRANGER, "Your quote is ready");

    assertThat(resolution.dropped()).isFalse();
    assertThat(resolution.recipient()).isEqualTo(PROSPECT);
    assertThat(resolution.intendedRecipient()).isEqualTo(STRANGER);
    assertThat(resolution.redirected()).isTrue();
    assertThat(resolution.subject())
        .isEqualTo("[Playground → %s] Your quote is ready".formatted(STRANGER));
  }

  @Test
  @DisplayName("a real tenant's mail is untouched")
  void passesThroughForRealTenants() {
    var resolution = policy.resolveFor(REAL, STRANGER, "Your quote is ready");

    assertThat(resolution.dropped()).isFalse();
    assertThat(resolution.recipient()).isEqualTo(STRANGER);
    assertThat(resolution.redirected()).isFalse();
    assertThat(resolution.subject()).isEqualTo("Your quote is ready");
  }

  @Test
  @DisplayName("sandboxed with no registration address: drop, never fall through")
  void dropsWhenThereIsNowhereToRedirect() {
    var resolution = policy.resolveFor(SANDBOXED_WITHOUT_ADDRESS, STRANGER, "Your quote is ready");

    assertThat(resolution.dropped()).isTrue();
    assertThat(resolution.recipient()).isNull();
  }

  @Test
  @DisplayName("an unresolvable tenant fails closed")
  void failsClosedForUnknownTenant() {
    assertThat(policy.resolveFor(UNKNOWN, STRANGER, "subject").dropped()).isTrue();
  }

  @Test
  @DisplayName("missing tenant fails loudly before sandbox lookup")
  void missingTenantFailsLoudly() {
    assertThatThrownBy(() -> policy.resolveFor(null, "ops@fabricos.example", "subject"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Tenant id must be known");
  }

  // --- verification codes: the path that never touches the outbox ---

  @Test
  @DisplayName("a verification code in a sandboxed tenant reaches the prospect, not the stranger")
  void verificationEmailIsRedirected() {
    var resolution = policy.resolveWithoutSubject(SANDBOXED, STRANGER, true);

    assertThat(resolution.dropped()).isFalse();
    assertThat(resolution.recipient()).isEqualTo(PROSPECT);
  }

  @Test
  @DisplayName("a sandboxed tenant cannot send an SMS: there is nowhere safe for it to go")
  void verificationSmsIsDropped() {
    var resolution = policy.resolveWithoutSubject(SANDBOXED, "+447700900123", false);

    assertThat(resolution.dropped()).isTrue();
  }

  @Test
  @DisplayName("a real tenant's SMS is untouched")
  void verificationSmsPassesThroughForRealTenants() {
    var resolution = policy.resolveWithoutSubject(REAL, "+447700900123", false);

    assertThat(resolution.dropped()).isFalse();
    assertThat(resolution.recipient()).isEqualTo("+447700900123");
  }

  private TenantAccessPort stubPort() {
    Map<UUID, EmailSandbox> sandboxes =
        Map.of(
            SANDBOXED, EmailSandbox.redirectingTo(PROSPECT),
            SANDBOXED_WITHOUT_ADDRESS, EmailSandbox.withoutRecipient(),
            REAL, EmailSandbox.off());

    return new TenantAccessPort() {
      @Override
      public boolean isWritable(UUID tenantId) {
        return true;
      }

      @Override
      public boolean isDemoMode(UUID tenantId) {
        return false;
      }

      @Override
      public EmailSandbox emailSandbox(UUID tenantId) {
        // Mirrors TenantAccessAdapter: an unresolved tenant fails closed.
        return sandboxes.getOrDefault(tenantId, EmailSandbox.withoutRecipient());
      }
    };
  }
}
