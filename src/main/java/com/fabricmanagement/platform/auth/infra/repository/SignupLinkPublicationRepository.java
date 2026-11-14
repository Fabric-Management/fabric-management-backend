package com.fabricmanagement.platform.auth.infra.repository;

import com.fabricmanagement.platform.auth.domain.event.SignupVerificationRequestedEvent;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The Spring Modulith publication log keeps each published event, serialized, until the general
 * 30-day purge. A {@link SignupVerificationRequestedEvent} carries the raw signup link, so its
 * publications are removed as soon as the link cannot be needed from there any more.
 *
 * <p>Runs on the primary data source ({@code fabric_app}), inside the caller's transaction; the
 * table has no row-level security.
 */
@Repository
public class SignupLinkPublicationRepository {

  static final String DELETE_DELIVERED_SQL =
      """
      DELETE FROM public.event_publication
      WHERE event_type = ?
        AND completion_date IS NOT NULL
      """;

  static final String DELETE_UNDELIVERED_SQL =
      """
      DELETE FROM public.event_publication
      WHERE event_type = ?
        AND completion_date IS NULL
        AND publication_date < ?
      """;

  private final JdbcTemplate jdbc;

  public SignupLinkPublicationRepository(DataSource dataSource) {
    this.jdbc = new JdbcTemplate(dataSource);
  }

  /**
   * Removes the publications of signup verification events that were delivered: the mail is queued,
   * and the event has nothing left to do.
   *
   * @return the number of publications removed
   */
  public int deleteDelivered() {
    return jdbc.update(DELETE_DELIVERED_SQL, SignupVerificationRequestedEvent.class.getName());
  }

  /**
   * Removes undelivered publications older than {@code unusableBefore}: their link has expired, so
   * a retry would only mail a dead link. The mail was never queued; the caller reports how many.
   *
   * @return the number of publications removed
   */
  public int deleteUndeliveredBefore(Instant unusableBefore) {
    return jdbc.update(
        DELETE_UNDELIVERED_SQL,
        SignupVerificationRequestedEvent.class.getName(),
        Timestamp.from(unusableBefore));
  }
}
