package com.minduc.happabi.listener;

import com.minduc.happabi.dto.event.S3ObjectDeleteRequestedEvent;
import com.minduc.happabi.integration.sqs.SqsFileCleanupPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ObjectDeleteEventListener {

    private final SqsFileCleanupPublisher fileCleanupPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onS3ObjectDeleteRequested(S3ObjectDeleteRequestedEvent event) {
        try {
            fileCleanupPublisher.publishDeleteObject(event.key(), event.reason());
        } catch (RuntimeException e) {
            log.warn("[SQS] Failed to publish S3 delete message after commit: key={} reason={}",
                    event.key(), event.reason(), e);
        }
    }
}
