package com.minduc.happabi.listener;

import com.minduc.happabi.dto.event.BusinessMetricRequestedEvent;
import com.minduc.happabi.observability.document.BusinessMetricDocument;
import com.minduc.happabi.repository.BusinessMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessMetricRequestedEventListener {

    private final BusinessMetricRepository businessMetricRepository;

    @Async("appTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBusinessMetricRequested(BusinessMetricRequestedEvent event) {
        try {
            BusinessMetricDocument metric = new BusinessMetricDocument();
            metric.setEventId(event.eventId().toString());
            metric.setEventType(event.eventType());
            metric.setTimestamp(event.timestamp());
            metric.setAmount(event.amount());
            metric.setStatus(event.status());
            businessMetricRepository.save(metric);
        } catch (RuntimeException e) {
            log.warn("[Metrics] Failed to persist async business metric: eventId={} eventType={}",
                    event.eventId(), event.eventType(), e);
        }
    }
}
