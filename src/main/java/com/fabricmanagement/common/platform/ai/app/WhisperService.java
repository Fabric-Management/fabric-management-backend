package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.ai.config.AIProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Whisper Service - Handles audio transcription via OpenAI Whisper API.
 *
 * <p>Converts audio files (mp3, wav, etc.) to text using OpenAI's Whisper model.</p>
 */
@Service
@Slf4j
public class WhisperService {

    private static final String OPENAI_WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String WHISPER_MODEL = "whisper-1";
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB (OpenAI limit)

    private final AIProperties aiProperties;
    private final RestTemplate restTemplate;

    public WhisperService(AIProperties aiProperties) {
        this.aiProperties = aiProperties;
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000); // 60 seconds for file upload
        factory.setReadTimeout(60000);
        return new RestTemplate(factory);
    }

    /**
     * Transcribe audio file to text.
     *
     * @param audioFile audio file (mp3, wav, etc.)
     * @return transcribed text
     * @throws IllegalStateException if AI features are disabled or API key is missing
     * @throws IllegalArgumentException if file is invalid or too large
     * @throws RuntimeException if API call fails
     */
    @Transactional(readOnly = true)
    public String transcribe(MultipartFile audioFile) {
        if (!aiProperties.getEnabled()) {
            throw new IllegalStateException("AI features are disabled");
        }

        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = TenantContext.getCurrentUserId();
        
        log.info("Whisper transcription request: tenantId={}, userId={}, fileName={}, fileSize={} bytes", 
            tenantId, userId, audioFile.getOriginalFilename(), audioFile.getSize());

        // Validate file
        validateFile(audioFile);

        // Check API key
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("AI API key is not configured. Please set AI_API_KEY in .env file or as environment variable.");
        }

        try {
            // Prepare request body
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // Add file
            body.add("file", audioFile.getResource());
            
            // Add model
            body.add("model", WHISPER_MODEL);
            
            // Use text response format (simpler, just returns the text)
            body.add("response_format", "text");

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(aiProperties.getApiKey());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Call OpenAI Whisper API
            ResponseEntity<String> response = restTemplate.exchange(
                OPENAI_WHISPER_API_URL,
                HttpMethod.POST,
                requestEntity,
                String.class
            );

            String transcribedText = response.getBody();
            if (transcribedText == null || transcribedText.isBlank()) {
                log.warn("Empty transcription result from OpenAI");
                throw new RuntimeException("Empty transcription result from OpenAI");
            }

            // Post-process to fix common Whisper transcription errors for textile terms
            transcribedText = fixTextileTermTranscription(transcribedText);

            log.info("Whisper transcription successful: tenantId={}, userId={}, textLength={}", 
                tenantId, userId, transcribedText.length());

            return transcribedText.trim();

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("OpenAI API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("OpenAI API error: " + e.getMessage(), e);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("OpenAI server error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("OpenAI server error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error calling OpenAI Whisper API", e);
            throw new RuntimeException("Failed to transcribe audio: " + e.getMessage(), e);
        }
    }

    /**
     * Validate audio file.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("File size exceeds maximum limit: %d bytes (max: %d bytes)", 
                    file.getSize(), MAX_FILE_SIZE)
            );
        }

        String contentType = file.getContentType();
        if (contentType == null || !isValidAudioType(contentType)) {
            throw new IllegalArgumentException(
                String.format("Invalid file type: %s. Supported types: audio/mpeg, audio/wav, audio/webm, audio/m4a, audio/x-m4a, audio/mp3", 
                    contentType)
            );
        }
    }

    /**
     * Check if content type is a valid audio format.
     */
    private boolean isValidAudioType(String contentType) {
        return contentType != null && (
            contentType.equals("audio/mpeg") ||
            contentType.equals("audio/mp3") ||
            contentType.equals("audio/wav") ||
            contentType.equals("audio/webm") ||
            contentType.equals("audio/m4a") ||
            contentType.equals("audio/x-m4a") ||
            contentType.startsWith("audio/")
        );
    }

    /**
     * Fix common Whisper transcription errors for textile technical terms.
     * 
     * <p>Whisper sometimes incorrectly transcribes "30'a 1" or "30'a bir" as "31".
     * This method corrects such cases when they appear before textile material names.</p>
     * 
     * <p>Examples:</p>
     * <ul>
     *   <li>"31 gabardin" → "30/1 gabardin"</li>
     *   <li>"41 pamuk" → "40/1 pamuk"</li>
     *   <li>"21 keten" → "20/1 keten"</li>
     * </ul>
     * 
     * @param text transcribed text from Whisper
     * @return corrected text
     */
    private String fixTextileTermTranscription(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // Known textile material names (Turkish and English)
        String[] textileMaterials = {
            "gabardin", "gabardine", "pamuk", "cotton", "keten", "linen",
            "polyester", "yün", "wool", "poplin", "kadife", "velvet",
            "denim", "jean", "kumaş", "fabric", "fiber"
        };

        // Pattern: number between 20-99 followed by textile material
        // Rule: If number = 30+1 (31), 40+1 (41), 20+1 (21), etc., convert to ratio format
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\b(2[1-9]|3[1-9]|4[1-9]|5[1-9]|6[1-9]|7[1-9]|8[1-9]|9[1-9])\\s+(" +
            String.join("|", textileMaterials) + ")", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher matcher = pattern.matcher(text);
        StringBuffer corrected = new StringBuffer();

        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            String material = matcher.group(2);

            // Check if number could be a ratio sum (e.g., 31 = 30+1, 41 = 40+1)
            // Common ratios: 20/1, 30/1, 40/1, 40/2, 50/1, 60/1
            String replacement = null;
            
            if (number >= 21 && number <= 29) {
                // Likely 20/1 (20+1=21) or similar
                int base = (number / 10) * 10;
                int second = number % 10;
                if (second == 1 && base >= 20) {
                    replacement = base + "/1 " + material;
                }
            } else if (number >= 31 && number <= 99) {
                int base = (number / 10) * 10;
                int second = number % 10;
                
                // 30/1 (30+1=31), 40/1 (40+1=41), 40/2 (40+2=42), etc.
                if (second == 1 && base >= 30) {
                    replacement = base + "/1 " + material;
                } else if (second == 2 && base >= 40) {
                    // 40/2, 50/2, etc.
                    replacement = base + "/2 " + material;
                } else if (second == 0 && base >= 20) {
                    // 30, 40, 50 - might be just the base number, keep as is
                    replacement = null;
                }
            }

            if (replacement != null) {
                log.debug("Corrected Whisper transcription: '{} {}' → '{}'", 
                    number, material, replacement);
                matcher.appendReplacement(corrected, java.util.regex.Matcher.quoteReplacement(replacement));
            } else {
                // No correction needed, keep original
                matcher.appendReplacement(corrected, matcher.group(0));
            }
        }
        matcher.appendTail(corrected);

        return corrected.toString();
    }
}

