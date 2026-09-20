package com.fabricmanagement.flowboard.task.infra.repository;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TaskTransitionTransactionGuard {
  private final JdbcTemplate jdbc;

  public TaskTransitionTransactionGuard(@Qualifier("dataSource") DataSource dataSource) {
    jdbc = new JdbcTemplate(dataSource);
  }

  public void setBoundedLockTimeout() {
    jdbc.execute("SET LOCAL lock_timeout = '5s'");
  }
}
