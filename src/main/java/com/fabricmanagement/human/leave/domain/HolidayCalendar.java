package com.fabricmanagement.human.leave.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "human_holiday_calendar",
    schema = "human",
    indexes = {
      @Index(name = "idx_holiday_calendar_country", columnList = "tenant_id,country_code")
    })
@Getter
@Setter
@NoArgsConstructor
public class HolidayCalendar extends BaseEntity {

  @Column(name = "country_code", nullable = false, length = 8)
  private String countryCode;

  @Column(name = "calendar_year", nullable = false)
  private Integer calendarYear;

  @Column(name = "entries", nullable = false, columnDefinition = "jsonb")
  private String entries;

  @Column(name = "version_tag", length = 50)
  private String versionTag;

  @Builder
  public HolidayCalendar(
      String countryCode, Integer calendarYear, String entries, String versionTag) {
    this.countryCode = countryCode;
    this.calendarYear = calendarYear;
    this.entries = entries;
    this.versionTag = versionTag;
  }

  @Override
  protected String getModuleCode() {
    return "HHC";
  }
}
