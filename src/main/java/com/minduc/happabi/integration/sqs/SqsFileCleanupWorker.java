package com.minduc.happabi.integration.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.message.S3ObjectDeleteMessage;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.sqs.file-cleanup-worker-enabled", havingValue = "true")
public class SqsFileCleanupWorker {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final IS3Service s3Service;

    @Value("${aws.sqs.file-cleanup-queue-url}")
    private String fileCleanupQueueUrl;

    @Scheduled(fixedDelayString = "${aws.sqs.file-cleanup-poll-delay-ms:5000}")
    public void pollFileCleanupQueue() {
        if (fileCleanupQueueUrl == null || fileCleanupQueueUrl.isBlank()) {
            log.warn("[SQS] File cleanup worker is enabled but queue URL is missing.");
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(fileCleanupQueueUrl)
                        .maxNumberOfMessages(5)
                        .waitTimeSeconds(5)
                        .build())
                .messages();

        for (Message message : messages) {
            processMessage(message);
        }
    }

    private void processMessage(Message sqsMessage) {
        try {
            S3ObjectDeleteMessage message = objectMapper.readValue(sqsMessage.body(), S3ObjectDeleteMessage.class);
            if (!S3ObjectDeleteMessage.TYPE.equals(message.type())) {
                log.warn("[SQS] Unknown file cleanup message type={}", message.type());
                deleteMessage(sqsMessage);
                return;
            }

            s3Service.delete(message.key());
            deleteMessage(sqsMessage);
            log.info("[SQS] Processed S3 delete message: key={} reason={}",
                    message.key(), message.reason());
        } catch (Exception e) {
            log.warn("[SQS] Failed to process file cleanup message. It will be retried: messageId={}",
                    sqsMessage.messageId(), e);
        }
    }

    private void deleteMessage(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(fileCleanupQueueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
