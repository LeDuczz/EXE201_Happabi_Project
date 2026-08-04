package com.minduc.happabi.service.audit.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuditErrorCode;
import com.minduc.happabi.service.audit.IAuditSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditSearchService implements IAuditSearchService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int SUGGESTION_LIMIT = 8;
    private static final String TIMESTAMP_FIELD = "@timestamp";
    private static final List<String> SEARCH_FIELDS = List.of(
            "action", "actor_id", "actor_role", "target_resource_type",
            "target_resource_id", "status", "reason", "ip_address");

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
    public Page<Map<String, Object>> searchLogs(String searchTerm, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        String indexPattern = auditIndexPrefix + "*";

        try {
            SearchRequest searchRequest = SearchRequest.of(s -> {
                s.index(indexPattern)
                        .allowNoIndices(true)
                        .ignoreUnavailable(true)
                        .from((int) pageable.getOffset())
                        .size(pageable.getPageSize())
                        .sort(so -> so.field(
                                f -> f.field(TIMESTAMP_FIELD)
                                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));

                Query query = buildQuery(searchTerm, fromDate, toDate);
                if (query != null) {
                    s.query(query);
                }
                return s;
            });

            @SuppressWarnings("rawtypes")
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            List<Map<String, Object>> content = response.hits().hits().stream()
                    .map(Hit::source)
                    .map(this::typedSource)
                    .map(this::mapTo)
                    .collect(Collectors.toList());

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            return new PageImpl<>(content, pageable, total);

        } catch (IOException e) {
            log.error("Failed to search audit logs in Elasticsearch", e);
            throw new AppException(AuditErrorCode.AUDIT_LOG_SEARCH_FAILED);
        }
    }

    @Override
    public List<String> suggestSearchTerms(String searchTerm) {
        String indexPattern = auditIndexPrefix + "*";

        try {
            SearchRequest searchRequest = SearchRequest.of(s -> {
                s.index(indexPattern)
                        .allowNoIndices(true)
                        .ignoreUnavailable(true)
                        .size(20)
                        .sort(so -> so.field(
                                f -> f.field(TIMESTAMP_FIELD)
                                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));

                Query query = buildTextQuery(searchTerm);
                if (query != null) {
                    s.query(query);
                }
                return s;
            });

            @SuppressWarnings("rawtypes")
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            Set<String> suggestions = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::typedSource)
                    .flatMap(source -> suggestionValues(source).stream())
                    .filter(value -> matchesSuggestion(searchTerm, value))
                    .limit(SUGGESTION_LIMIT)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            return List.copyOf(suggestions);
        } catch (IOException e) {
            log.error("Failed to suggest audit log search terms in Elasticsearch", e);
            throw new AppException(AuditErrorCode.AUDIT_LOG_SEARCH_FAILED);
        }
    }

    private Query buildQuery(String searchTerm, LocalDate fromDate, LocalDate toDate) {
        Query textQuery = buildTextQuery(searchTerm);
        Query dateRangeQuery = buildDateRangeQuery(fromDate, toDate);

        if (textQuery == null) {
            return dateRangeQuery;
        }
        if (dateRangeQuery == null) {
            return textQuery;
        }

        return Query.of(q -> q.bool(b -> b
                .must(textQuery)
                .filter(dateRangeQuery)));
    }

    private Query buildTextQuery(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return null;
        }

        return Query.of(q -> q.queryString(qs -> qs
                .query("*" + searchTerm.trim() + "*")
                .fields(SEARCH_FIELDS)
                .analyzeWildcard(true)
                .defaultOperator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)));
    }

    private Query buildDateRangeQuery(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return null;
        }

        return Query.of(q -> q.range(r -> {
            r.field(TIMESTAMP_FIELD);
            if (fromDate != null) {
                r.gte(JsonData.of(fromDate.atStartOfDay(BUSINESS_ZONE).toInstant().toString()));
            }
            if (toDate != null) {
                r.lte(JsonData.of(toDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).minusNanos(1).toInstant().toString()));
            }
            return r;
        }));
    }

    private List<String> suggestionValues(Map<String, Object> source) {
        return SEARCH_FIELDS.stream()
                .map(source::get)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private boolean matchesSuggestion(String searchTerm, String value) {
        return searchTerm == null
                || searchTerm.isBlank()
                || value.toLowerCase().contains(searchTerm.trim().toLowerCase());
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

    private Map<String, Object> typedSource(Map<?, ?> source) {
        Map<String, Object> typed = new HashMap<>();
        source.forEach((key, value) -> typed.put(String.valueOf(key), value));
        return typed;
    }
}
