package com.minduc.happabi.integration.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.openai.OpenAiCccdOcrResult;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.OcrErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiVisionOcrClient {

    private static final int MAX_TRANSIENT_ATTEMPTS = 3;

    private static final Set<Integer> TRANSIENT_STATUS_CODES = Set.of(429, 502, 503, 504);

    private static final String OCR_PROMPT = """
            You are extracting fields from the front side of a Vietnamese citizen ID card (CCCD).
            Extract only information visible in the image. Do not infer or invent missing values.
            Return null for any field that is not readable.
            Use ISO-8601 yyyy-MM-dd for date of birth.
            Keep Vietnamese names and addresses with accents exactly as shown when readable.
            Set requiresManualReview=true when any required field is missing or low confidence.
            """;

    private final ObjectMapper objectMapper;

    @Qualifier("openAiRestClient")
    private final RestClient openAiRestClient;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.api-key:}")
    private String apiKey;

    public OpenAiCccdOcrResult extractCccdFront(MultipartFile frontImage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException(OcrErrorCode.OCR_CONFIGURATION_MISSING, "OPENAI_API_KEY is not configured.");
        }

        String dataUrl = toDataUrl(frontImage);
        Map<String, Object> requestBody = buildRequestBody(dataUrl);

        OpenAiStatusException lastStatusException = null;
        RestClientException lastClientException = null;

        for (int attempt = 1; attempt <= MAX_TRANSIENT_ATTEMPTS; attempt++) {
            try {
                JsonNode response = callOpenAi(requestBody);
                return parseResult(response);
            } catch (OpenAiStatusException e) {
                lastStatusException = e;
                if (!isTransient(e.statusCode()) || attempt == MAX_TRANSIENT_ATTEMPTS) {
                    log.warn("[OCR] OpenAI returned non-success status={} attempt={}/{} body={}",
                            e.statusCode(), attempt, MAX_TRANSIENT_ATTEMPTS, truncate(e.responseBody()));
                    throw new AppException(OcrErrorCode.OCR_PROVIDER_UNAVAILABLE,
                            "OpenAI status=" + e.statusCode() + " after " + attempt + " attempt(s)");
                }
                log.warn("[OCR] OpenAI transient status={} attempt={}/{} body={}; retrying",
                        e.statusCode(), attempt, MAX_TRANSIENT_ATTEMPTS, truncate(e.responseBody()));
                sleepBeforeRetry(attempt);
            } catch (RestClientException e) {
                lastClientException = e;
                if (attempt == MAX_TRANSIENT_ATTEMPTS) {
                    log.warn("[OCR] OpenAI request failed after {} attempts: {}",
                            MAX_TRANSIENT_ATTEMPTS, e.getMessage());
                    throw new AppException(OcrErrorCode.OCR_PROVIDER_UNAVAILABLE, e);
                }
                log.warn("[OCR] OpenAI request failed attempt={}/{}: {}; retrying",
                        attempt, MAX_TRANSIENT_ATTEMPTS, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }

        if (lastStatusException != null) {
            throw new AppException(OcrErrorCode.OCR_PROVIDER_UNAVAILABLE,
                    "OpenAI status=" + lastStatusException.statusCode());
        }
        throw new AppException(OcrErrorCode.OCR_PROVIDER_UNAVAILABLE, lastClientException);
    }

    private JsonNode callOpenAi(Map<String, Object> requestBody) {
        try {
            return openAiRestClient.post()
                    .uri("/responses")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseBody) -> {
                        String errorBody = "";
                        try {
                            errorBody = new String(responseBody.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        } catch (IOException ignored) {
                        }
                        throw new OpenAiStatusException(responseBody.getStatusCode().value(), errorBody);
                    })
                    .body(JsonNode.class);
        } catch (OpenAiStatusException | RestClientException e) {
            throw e;
        }
    }

    private boolean isTransient(int statusCode) {
        return TRANSIENT_STATUS_CODES.contains(statusCode);
    }

    private void sleepBeforeRetry(int attempt) {
        long delayMs = switch (attempt) {
            case 1 -> 1000L;
            case 2 -> 3000L;
            default -> 5000L;
        };

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(
                    OcrErrorCode.OCR_PROVIDER_UNAVAILABLE,
                    "OCR retry was interrupted.",
                    e
            );
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private String toDataUrl(MultipartFile file) {
        try {
            String encoded = Base64.getEncoder().encodeToString(file.getBytes());
            return "data:" + file.getContentType() + ";base64," + encoded;
        } catch (IOException e) {
            throw new AppException(OcrErrorCode.OCR_EXTRACTION_FAILED, "Cannot read uploaded image.", e);
        }
    }

    private Map<String, Object> buildRequestBody(String dataUrl) {
        return Map.of(
                "model", model,
                "input", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "input_text", "text", OCR_PROMPT),
                                Map.of("type", "input_image", "image_url", dataUrl)
                        )
                )),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "vietnamese_cccd_front_ocr",
                                "strict", true,
                                "schema", schema()
                        )
                )
        );
    }

    private Map<String, Object> schema() {
        Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));
        Map<String, Object> nullableNumber = Map.of("type", List.of("number", "null"), "minimum", 0, "maximum", 1);

        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "cccdNumber", nullableString,
                        "cccdName", nullableString,
                        "cccdDob", Map.of(
                                "type", List.of("string", "null"),
                                "description", "Date of birth in yyyy-MM-dd format"
                        ),
                        "cccdAddress", nullableString,
                        "confidence", nullableNumber,
                        "requiresManualReview", Map.of("type", "boolean"),
                        "warnings", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )
                ),
                "required", List.of(
                        "cccdNumber",
                        "cccdName",
                        "cccdDob",
                        "cccdAddress",
                        "confidence",
                        "requiresManualReview",
                        "warnings"
                )
        );
    }

    private OpenAiCccdOcrResult parseResult(JsonNode response) {
        String outputText = findOutputText(response);
        if (outputText == null || outputText.isBlank()) {
            throw new AppException(OcrErrorCode.OCR_RESPONSE_INVALID, "Missing output text.");
        }

        try {
            return objectMapper.readValue(outputText, OpenAiCccdOcrResult.class);
        } catch (JsonProcessingException e) {
            log.warn("[OCR] Failed to parse OpenAI OCR JSON: {}", outputText);
            throw new AppException(OcrErrorCode.OCR_RESPONSE_INVALID, "Invalid OCR JSON.", e);
        }
    }

    private String findOutputText(JsonNode response) {
        if (response == null) {
            return null;
        }

        JsonNode outputText = response.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }

        JsonNode output = response.get("output");
        if (output == null || !output.isArray()) {
            return null;
        }

        for (JsonNode item : output) {
            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                JsonNode text = contentItem.get("text");
                if (text != null && text.isTextual()) {
                    return text.asText();
                }
            }
        }
        return null;
    }

    private static class OpenAiStatusException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        private OpenAiStatusException(int statusCode, String responseBody) {
            super("OpenAI status=" + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        private int statusCode() {
            return statusCode;
        }

        private String responseBody() {
            return responseBody;
        }
    }
}
