package com.minduc.happabi.service.openai;

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
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiVisionOcrClient {

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

        try {
            JsonNode response = openAiRestClient.post()
                    .uri("/responses")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseBody) -> {
                        throw new AppException(OcrErrorCode.OCR_PROVIDER_UNAVAILABLE,
                                "OpenAI status=" + responseBody.getStatusCode());
                    })
                    .body(JsonNode.class);

            return parseResult(response);
        } catch (AppException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("[OCR] OpenAI request failed: {}", e.getMessage());
            throw new AppException(OcrErrorCode.OCR_PROVIDER_UNAVAILABLE, e);
        }
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
}
