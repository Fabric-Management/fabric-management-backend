package com.fabricmanagement.platform.dbprivilege;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;

/** Uses the operator's SQL resources; there is no separate Java violation predicate. */
final class DefaultAclGuard {
  private static final String VIOLATIONS = "db/privilege/default-acl-violations.sql";
  private static final String MEASUREMENTS = "db/privilege/effective-grants.sql";

  private DefaultAclGuard() {}

  static List<PrivilegeDifference> differences(Connection connection) throws SQLException {
    List<PrivilegeDifference> differences = new ArrayList<>();
    for (Entry entry : violations(connection)) {
      differences.add(entry.difference());
    }
    return differences.stream().sorted().toList();
  }

  static Set<Entry> violations(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(readScript(VIOLATIONS))) {
      return entries(result, true);
    }
  }

  static Set<Entry> snapshot(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      // Q0 (context), Q1 (table vectors), Q2 (all exploded defaults), Q3 (memberships).
      if (!statement.execute(readScript(MEASUREMENTS))
          || !statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)
          || !statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)) {
        throw new SQLException("Measurement resource must expose Q0, Q1 and Q2 result sets");
      }
      try (ResultSet result = statement.getResultSet()) {
        return entries(result, false);
      }
    }
  }

  private static Set<Entry> entries(ResultSet result, boolean tablesOnly) throws SQLException {
    Set<Entry> entries = new LinkedHashSet<>();
    while (result.next()) {
      entries.add(
          new Entry(
              result.getString("owner_role"),
              result.getString("schema_name"),
              tablesOnly ? "TABLES" : result.getString("object_type"),
              result.getString("grantee"),
              result.getString("privilege"),
              result.getBoolean("is_grantable")));
    }
    return Set.copyOf(entries);
  }

  private static String readScript(String path) {
    try (var input = new ClassPathResource(path).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read SQL script: " + path, exception);
    }
  }

  record Entry(
      String owner,
      String schema,
      String objectType,
      String grantee,
      String privilege,
      boolean grantable) {
    PrivilegeDifference difference() {
      return new PrivilegeDifference(
          owner + " / " + schema + " / " + objectType,
          grantee,
          "surplus default: " + privilege + ", grantable=" + grantable);
    }
  }
}
