package com.fabricmanagement.flowboard.dashboard.dto;

public record MetricWithTrend(int current, int previous, double changeRate) {
  public static MetricWithTrend of(int current, int previous) {
    double rate =
        previous == 0
            ? (current > 0 ? 100.0 : 0.0)
            : ((double) (current - previous) / previous) * 100.0;
    return new MetricWithTrend(current, previous, rate);
  }
}
