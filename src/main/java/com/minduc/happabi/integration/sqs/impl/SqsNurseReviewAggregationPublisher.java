package com.minduc.happabi.integration.sqs.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.message.NurseReviewAggregationMessage;
import com.minduc.happabi.integration.sqs.INurseReviewAggregationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqsNurseReviewAggregationPublisher implements INurseReviewAggregationPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.nurse-review-aggregation-queue-url:}")
    private String queueUrl;

    @Override
    public void publishRecalculate(UUID nurseProfileId, UUID reviewId, String reason) {
        if (nurseProfileId == null) {
            return;
        }
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("[SQS] Nurse review aggregation queue is not configured. Skip message for nurseProfileId={}",
                    nurseProfileId);
            return;
        }

        NurseReviewAggregationMessage message = new NurseReviewAggregationMessage(
                NurseReviewAggregationMessage.TYPE,
                nurseProfileId,
                reviewId,
                reason,
                OffsetDateTime.now()
        );

        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(message))
                    .build());
            log.info("[SQS] Published nurse review aggregation message: nurseProfileId={} reviewId={}",
                    nurseProfileId, reviewId);
        } catch (JsonProcessingException e) {
            log.error("[SQS] Failed to serialize nurse review aggregation message: nurseProfileId={}",
                    nurseProfileId, e);

        } catch (SqsException e) {
            log.warn("[SQS] AWS SQS rejected nurse review aggregation message. nurseProfileId={} statusCode={} message={}",
                    nurseProfileId,
                    e.statusCode(),
                    e.awsErrorDetails().errorMessage());

        } catch (SdkClientException e) {
            log.warn("[SQS] Cannot connect to AWS SQS while publishing nurse review aggregation. nurseProfileId={} error={}",
                    nurseProfileId, e.getMessage());

        } catch (Exception e) {
            log.error("[SQS] Unexpected nurse review aggregation publish failure: nurseProfileId={}",
                    nurseProfileId, e);
        }
    }
}
