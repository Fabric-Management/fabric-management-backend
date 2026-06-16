package com.fabricmanagement.finance.fxexposure.api.controller;

import com.fabricmanagement.finance.fxexposure.app.FxExposureService;
import com.fabricmanagement.finance.fxexposure.dto.FxExposureSummaryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/fx-exposure")
@RequiredArgsConstructor
@Tag(name = "Finance - FX Exposure", description = "FX Exposure summary operations")
public class FxExposureController {

  private final FxExposureService fxExposureService;

  @GetMapping("/summary")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  public FxExposureSummaryDto getSummary() {
    return fxExposureService.getSummary();
  }
}
