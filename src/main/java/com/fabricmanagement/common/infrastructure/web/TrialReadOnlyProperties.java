package com.fabricmanagement.common.infrastructure.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for read-only tenant write-guard escape hatches. */
@Component
@ConfigurationProperties(prefix = "application.trial.read-only")
public class TrialReadOnlyProperties {

  private List<String> allowPaths = new ArrayList<>();

  public List<String> getAllowPaths() {
    return allowPaths;
  }

  public void setAllowPaths(List<String> allowPaths) {
    this.allowPaths = allowPaths != null ? new ArrayList<>(allowPaths) : new ArrayList<>();
  }
}
