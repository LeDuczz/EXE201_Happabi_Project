package com.minduc.happabi.service.audit.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BusinessMetricsErrorCode;
import com.minduc.happabi.service.audit.IAuditSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditSearchService implements IAuditSearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${observability.outbox.projector.audit-index-prefix:happabi-audit}")
    private String auditIndexPrefix;

    /**
     * Searches for audit logs in Elasticsearch.
     * Maps snake_case ES fields to camelCase DTO fields for frontend compatibility.
     *
     * @param pageable Pagination information.
     * @return A page of audit log entries.
     */
    @Override
    public Page<Map<String, Object>> searchLogs(String searchTerm, Pageable pageable) {
        String indexPattern = auditIndexPrefix + "*";

        try {
            SearchRequest searchRequest = SearchRequest.of(s -> {
                s.index(indexPattern)
                        .from((int) pageable.getOffset())
                        .size(pageable.getPageSize())
                        .sort(so -> so.field(
                                f -> f.field("@timestamp")
                                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));

                if (searchTerm != null && !searchTerm.isBlank()) {
                    s.query(q -> q.queryString(qs -> qs
                            .query("*" + searchTerm + "*")
                            .fields(List.of("action", "actor_id", "actor_role", "target_resource_type",
                                    "target_resource_id", "status", "reason", "ip_address"))
                            .analyzeWildcard(true)
                            .defaultOperator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)));
                }
                return s;
            });

            @SuppressWarnings("rawtypes")
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            @SuppressWarnings({ "unchecked", "rawtypes" })
            List<Map<String, Object>> content = response.hits().hits().stream()
                    .map(Hit::source)
                    .map(source -> mapTo((Map<String, Object>) source))
                    .collect(Collectors.toList());

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            return new PageImpl<>(content, pageable, total);

        } catch (IOException e) {
            log.error("Failed to search audit logs in Elasticsearch", e);
            throw new AppException(BusinessMetricsErrorCode.GET_DAILY_GMV_LAST_30_DAYS_ERROR_CODE);
        }
    }

    private Map<String, Object> mapTo(Map<String, Object> source) {
        if (source == null)
            return null;
        Map<String, Object> mapped = new HashMap<>(source);

        mapped.put("id", source.get("audit_event_id"));
        mapped.put("actorId", source.get("actor_id"));
        mapped.put("actorRole", source.get("actor_role"));
        mapped.put("targetResourceType", source.get("target_resource_type"));
        mapped.put("targetResourceId", source.get("target_resource_id"));
        mapped.put("ipAddress", source.get("ip_address"));
        mapped.put("createdAt", source.get("created_at"));

        return mapped;
    }
}
