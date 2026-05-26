package com.minduc.happabi.integration.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.message.S3ObjectDeleteMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqsFileCleanupPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.file-cleanup-queue-url:}")
    private String fileCleanupQueueUrl;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public void publishDeleteObject(String key, String reason) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (fileCleanupQueueUrl == null || fileCleanupQueueUrl.isBlank()) {
            log.warn("[SQS] File cleanup queue is not configured. Skip delete message for key={}", key);
            return;
        }

        S3ObjectDeleteMessage message = new S3ObjectDeleteMessage(
                S3ObjectDeleteMessage.TYPE,
                bucket,
                key,
                reason,
                OffsetDateTime.now()
        );

        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(fileCleanupQueueUrl)
                    .messageBody(objectMapper.writeValueAsString(message))
                    .build());
            log.info("[SQS] Published S3 delete message: key={} reason={}", key, reason);
        } catch (JsonProcessingException e) {
            log.error("[SQS] Failed to serialize cleanup message for key={}", key, e);

        } catch (SqsException e) {
            log.warn("[SQS] AWS SQS rejected cleanup message. key={} statusCode={} message={}",
                    key,
                    e.statusCode(),
                    e.awsErrorDetails().errorMessage());

        } catch (SdkClientException e) {
            log.warn("[SQS] Cannot connect to AWS SQS while publishing cleanup message. key={} error={}",
                    key,
                    e.getMessage());

        } catch (Exception e) {
            log.error("[SQS] Unexpected cleanup publish failure for key={}", key, e);
        }
    }
}
