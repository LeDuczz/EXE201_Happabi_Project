package com.minduc.happabi.observability.outbox;

import com.minduc.happabi.entity.OutboxEvent;
import com.minduc.happabi.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "observability.outbox.projector", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxElasticsearchProjector {

    private static final DateTimeFormatter INDEX_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final OutboxEventRepository outboxEventRepository;

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUris;

    @Value("${observability.outbox.projector.batch-size:50}")
    private int batchSize;

    @Value("${observability.outbox.projector.retry-delay-seconds:30}")
    private int retryDelaySeconds;

    @Value("${observability.outbox.projector.max-retry:5}")
    private int maxRetry;

    @Value("${observability.outbox.projector.audit-index-prefix:happabi-audit}")
    private String auditIndexPrefix;

    @Value("${observability.audit.outbox.topic:audit.events}")
    private String auditTopic;

    private RestClient elasticsearchClient;

    @PostConstruct
    void initialize() {
        String baseUrl = elasticsearchUris.split(",")[0].trim();
        this.elasticsearchClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Scheduled(fixedDelayString = "${observability.outbox.projector.fixed-delay-ms:5000}")
    @Transactional
    public void projectPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findPublishable(
                OutboxStatus.PENDING,
                OffsetDateTime.now(ZoneOffset.UTC),
                PageRequest.of(0, batchSize));

        for (OutboxEvent event : events) {
            try {
                event.markProcessing();
                if (!auditTopic.equals(event.getTopic())) {
                    event.markFailed("Unsupported outbox topic: " + event.getTopic());
                    continue;
                }

                indexAuditEvent(event);
                event.markProcessed();
            } catch (Exception e) {
                markFailure(event, e);
                log.warn("[Outbox] Failed to project event id={} topic={} retryCount={}",
                        event.getId(), event.getTopic(), event.getRetryCount(), e);
            }
        }
    }

    private void indexAuditEvent(OutboxEvent event) {
        String indexName = auditIndexPrefix + "-" + INDEX_DATE.format(OffsetDateTime.now(ZoneOffset.UTC));
        elasticsearchClient.put()
                .uri("/{index}/_doc/{id}", indexName, event.getEventKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(event.getPayload())
                .retrieve()
                .toBodilessEntity();
    }

    private void markFailure(OutboxEvent event, Exception e) {
        String error = shortError(e);
        if (event.getRetryCount() + 1 >= maxRetry) {
            event.markFailed(error);
            return;
        }
        int delay = (int) Math.min(3600, retryDelaySeconds * Math.pow(2, event.getRetryCount()));
        event.markRetry(error, delay);
    }

    private String shortError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
