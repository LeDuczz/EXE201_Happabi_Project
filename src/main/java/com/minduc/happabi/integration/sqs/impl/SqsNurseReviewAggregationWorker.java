package com.minduc.happabi.integration.sqs.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.message.NurseReviewAggregationMessage;
import com.minduc.happabi.service.review.INurseReviewAggregationMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.sqs.nurse-review-aggregation-worker-enabled", havingValue = "true")
public class SqsNurseReviewAggregationWorker {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final INurseReviewAggregationMessageHandler messageHandler;

    @Value("${aws.sqs.nurse-review-aggregation-queue-url}")
    private String queueUrl;

    @Scheduled(fixedDelayString = "${aws.sqs.nurse-review-aggregation-poll-delay-ms:5000}")
    public void pollNurseReviewAggregationQueue() {
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("[SQS] Nurse review aggregation worker is enabled but queue URL is missing.");
            return;
        }

        try {
            List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(5)
                            .build())
                    .messages();

            for (Message message : messages) {
                processMessage(message);
            }

        } catch (SqsException e) {
            log.warn("[SQS] AWS SQS rejected nurse review aggregation poll. statusCode={} message={}",
                    e.statusCode(), e.awsErrorDetails().errorMessage());

        } catch (SdkClientException e) {
            log.warn("[SQS] Cannot connect to AWS SQS for nurse review aggregation: {}", e.getMessage());

        } catch (Exception e) {
            log.error("[SQS] Unexpected error while polling nurse review aggregation queue", e);
        }
    }

    private void processMessage(Message sqsMessage) {
        try {
            NurseReviewAggregationMessage message = objectMapper.readValue(sqsMessage.body(),
                    NurseReviewAggregationMessage.class);
            if (!messageHandler.supports(message)) {
                log.warn("[SQS] Unknown nurse review aggregation message type={}", message.type());
                deleteMessage(sqsMessage);
                return;
            }

            messageHandler.handle(message);
            deleteMessage(sqsMessage);
            log.info("[SQS] Processed nurse review aggregation message: nurseProfileId={} reviewId={}",
                    message.nurseProfileId(), message.reviewId());
        } catch (SdkClientException e) {
            log.warn("[SQS] AWS unavailable while processing nurse review aggregation: messageId={} error={}",
                    sqsMessage.messageId(), e.getMessage());

        } catch (Exception e) {
            log.error("[SQS] Unexpected nurse review aggregation processing error: messageId={}",
                    sqsMessage.messageId(), e);
        }
    }

    private void deleteMessage(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());

        } catch (SqsException e) {
            log.warn("[SQS] Failed to delete nurse review aggregation message: messageId={} statusCode={} message={}",
                    message.messageId(), e.statusCode(), e.awsErrorDetails().errorMessage());

        } catch (SdkClientException e) {
            log.warn("[SQS] Cannot delete nurse review aggregation message: messageId={} error={}",
                    message.messageId(), e.getMessage());
        }
    }
}
