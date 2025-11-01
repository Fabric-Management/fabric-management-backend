package com.fabricmanagement.common.platform.ai.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.ai.app.WhisperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Whisper Controller - REST endpoint for audio transcription.
 *
 * <p>Accepts audio files and returns transcribed text using OpenAI Whisper API.</p>
 */
@RestController
@RequestMapping("/api/whisper")
@RequiredArgsConstructor
@Slf4j
public class WhisperController {

    private final WhisperService whisperService;

    /**
     * Transcribe audio file to text.
     *
     * <p>TenantContext is automatically set by JwtContextInterceptor.</p>
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

        log.info("Whisper transcription request: userId={}, fileName={}, fileSize={} bytes", 
            userId, audioFile.getOriginalFilename(), audioFile.getSize());

        String transcribedText = whisperService.transcribe(audioFile);

        log.info("Whisper transcription successful: userId={}, textLength={}", 
            userId, transcribedText.length());

        return ResponseEntity.ok(ApiResponse.success(transcribedText));
    }
}

