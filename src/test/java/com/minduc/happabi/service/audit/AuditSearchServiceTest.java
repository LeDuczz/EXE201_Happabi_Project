package com.minduc.happabi.service.audit;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.minduc.happabi.service.audit.impl.AuditSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditSearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private AuditSearchService service;

    @BeforeEach
    void setUp() {
        service = new AuditSearchService(elasticsearchClient);
        ReflectionTestUtils.setField(service, "auditIndexPrefix", "happabi-audit");
    }

    @Test
    void searchLogsBuildsTextAndDateRangeQuery() throws IOException {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(searchResponse(List.of(), 0));

        service.searchLogs(
                "LOGIN",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 4),
                PageRequest.of(1, 20)
        );

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(elasticsearchClient).search(requestCaptor.capture(), eq(Map.class));
        SearchRequest request = requestCaptor.getValue();

        assertThat(request.index()).containsExactly("happabi-audit*");
        assertThat(request.from()).isEqualTo(20);
        assertThat(request.size()).isEqualTo(20);
        assertThat(request.query().bool().must()).hasSize(1);
        assertThat(request.query().bool().filter()).hasSize(1);
    }

    @Test
    void searchLogsMapsElasticsearchAuditFieldsForFrontend() throws IOException {
        Map<String, Object> source = Map.of(
                "audit_event_id", "audit-1",
                "actor_id", "admin-1",
                "actor_role", "ADMIN",
                "action", "LOCK_USER",
                "target_resource_type", "USER",
                "target_resource_id", "user-1",
                "status", "SUCCESS",
                "ip_address", "127.0.0.1",
                "created_at", "2026-08-04T10:00:00Z",
                "metadata", Map.of("reason", "test")
        );
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(searchResponse(List.of(source), 1));

        Page<Map<String, Object>> result = service.searchLogs(null, null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst())
                .containsEntry("id", "audit-1")
                .containsEntry("actorId", "admin-1")
                .containsEntry("actorRole", "ADMIN")
                .containsEntry("targetResourceType", "USER")
                .containsEntry("targetResourceId", "user-1")
                .containsEntry("ipAddress", "127.0.0.1")
                .containsEntry("createdAt", "2026-08-04T10:00:00Z");
    }

    @Test
    void suggestSearchTermsReturnsUniqueRecentAuditValues() throws IOException {
        Map<String, Object> first = Map.of(
                "action", "LOGIN",
                "actor_role", "ADMIN",
                "target_resource_type", "AUTH",
                "status", "SUCCESS",
                "ip_address", "127.0.0.1"
        );
        Map<String, Object> second = Map.of(
                "action", "LOGIN_FAILED",
                "actor_role", "MOTHER",
                "target_resource_type", "AUTH",
                "status", "FAILED",
                "ip_address", "127.0.0.2"
        );
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(searchResponse(List.of(first, second), 2));

        List<String> suggestions = service.suggestSearchTerms("log");

        assertThat(suggestions).containsExactly("LOGIN", "LOGIN_FAILED");
    }

    private SearchResponse<Map> searchResponse(List<Map<String, Object>> sources, long total) {
        List<Hit<Map>> hits = sources.stream()
                .map(source -> Hit.<Map>of(hit -> hit
                        .index("happabi-audit-2026.08.04")
                        .id(source.getOrDefault("audit_event_id", source.getOrDefault("action", "audit")).toString())
                        .source(source)))
                .toList();

        return SearchResponse.of(response -> response
                .took(1)
                .timedOut(false)
                .shards(ShardStatistics.of(shards -> shards.total(1).successful(1).failed(0)))
                .hits(HitsMetadata.of(metadata -> metadata
                        .total(totalHits -> totalHits.value(total).relation(TotalHitsRelation.Eq))
                        .hits(hits))));
    }
}
