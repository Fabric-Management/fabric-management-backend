package com.fabricmanagement.platform.ai.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.ai.app.WhisperService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Whisper Controller - REST endpoint for audio transcription.
 *
 * <p>Accepts audio files and returns transcribed text using OpenAI Whisper API.
 */
@RestController
@RequestMapping("/api/v1/whisper")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Whisper", description = "Whisper operations")
public class WhisperController {

  private final WhisperService whisperService;

  /**
   * Transcribe audio file to text.
   *
   * <p>TenantContext is automatically set by JwtContextInterceptor.
   *
   * @param audioFile audio file (mp3, wav, etc.)
   * @return transcribed text
   */
  @PostMapping
  public ResponseEntity<ApiResponse<String>> transcribe(
      @RequestParam("file") MultipartFile audioFile) {

    // JwtContextInterceptor automatically sets TenantContext from JWT token
    UUID userId = TenantContext.getCurrentUserId();

    // Basic security: require authenticated user
    if (userId == null) {
      log.warn("Whisper request without authenticated user");
      return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
          .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
    }

    log.info(
        "Whisper transcription request: userId={}, fileName={}, fileSize={} bytes",
        userId,
        audioFile.getOriginalFilename(),
        audioFile.getSize());

    String transcribedText = whisperService.transcribe(audioFile);

    log.info(
        "Whisper transcription successful: userId={}, textLength={}",
        userId,
        transcribedText.length());

    return ResponseEntity.ok(ApiResponse.success(transcribedText));
  }
}
