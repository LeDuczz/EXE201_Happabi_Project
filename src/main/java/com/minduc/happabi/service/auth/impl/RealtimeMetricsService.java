package com.minduc.happabi.service.auth.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BusinessMetricsErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.service.auth.IRealtimeMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeMetricsService implements IRealtimeMetricsService {

    private final ElasticsearchClient elasticsearchClient;
    private final com.minduc.happabi.repository.UserRepository userRepository;

    @LogExecution
    @AuditAction(action = "READ", resourceType = "GMV")
    @Override
    public Map<String, Double> getDailyGmvLast30Days() {
        Map<String, Double> gmvByDate = new LinkedHashMap<>();
        Instant thirtyDays = Instant.now().minus(30, ChronoUnit.DAYS);
        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("happabi-business-metrics")
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m.term(t -> t.field("eventType").value("TRANSACTION_SUCCESS")))
                                    .must(m -> m.range(
                                            r -> r.field("timestamp").gte(JsonData.fromJson(thirtyDays.toString()))))))
                    .aggregations("gmv_per_day", a -> a
                            .dateHistogram(h -> h
                                    .field("timestamp").calendarInterval(CalendarInterval.Day))
                            .aggregations("total_amount", sub -> sub
                                    .sum(sum -> sum.field("amount"))))
                    .size(0) // Skip raw data
            );
            SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

            response.aggregations().get("gmv_per_day").dateHistogram().buckets().array().forEach(bucket -> {
                String date = bucket.keyAsString();
                double totalGmv = bucket.aggregations().get("total_amount").sum().value();
                gmvByDate.put(date, totalGmv);
            });

        } catch (Exception e) {
            log.error("Error fetching daily GMV from Elasticsearch. Instant: {}", thirtyDays, e);
            throw new AppException(BusinessMetricsErrorCode.GET_DAILY_GMV_LAST_30_DAYS_ERROR_CODE);
        }

        return gmvByDate;
    }

    @Override
    public Map<String, Long> getDailyBookingCountLast30Days() {
        Map<String, Long> countByDate = new LinkedHashMap<>();
        Instant thirtyDays = Instant.now().minus(30, ChronoUnit.DAYS);
        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("happabi-business-metrics")
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m.term(t -> t.field("eventType").value("TRANSACTION_SUCCESS")))
                                    .must(m -> m.range(
                                            r -> r.field("timestamp").gte(JsonData.fromJson(thirtyDays.toString()))))))
                    .aggregations("bookings_per_day", a -> a
                            .dateHistogram(h -> h
                                    .field("timestamp").calendarInterval(CalendarInterval.Day)))
                    .size(0));
            SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

            response.aggregations().get("bookings_per_day").dateHistogram().buckets().array().forEach(bucket -> {
                countByDate.put(bucket.keyAsString(), bucket.docCount());
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
        return countByDate;
    }

    @Override
    public Map<String, Long> getUserGrowthLast30Days() {
        Map<String, Long> growth = new LinkedHashMap<>();
        Instant thirtyDays = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Object[]> stats = userRepository.getUserGrowthStats(thirtyDays);

        for (Object[] row : stats) {
            growth.put(row[0].toString(), (Long) row[1]);
        }
        return growth;
    }

    @Override
    public Map<String, Long> getRoleDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        distribution.put("MOTHER", userRepository.countByRoleName(com.minduc.happabi.enums.UserRole.MOTHER));
        distribution.put("NURSE", userRepository.countByRoleName(com.minduc.happabi.enums.UserRole.NURSE));
        distribution.put("ADMIN", userRepository.countByRoleName(com.minduc.happabi.enums.UserRole.ADMIN));
        return distribution;
    }

    @Override
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>();

        long totalUsers = userRepository.count();
        long totalNurses = userRepository.countByRoleName(com.minduc.happabi.enums.UserRole.NURSE);

        summary.put("totalUsers", totalUsers);
        summary.put("totalNurses", totalNurses);

        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("happabi-business-metrics")
                    .query(q -> q.term(t -> t.field("eventType").value("TRANSACTION_SUCCESS")))
                    .aggregations("total_gmv", a -> a.sum(sum -> sum.field("amount")))
                    .size(0));
            SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
            double totalGmv = response.aggregations().get("total_gmv").sum().value();
            summary.put("totalGmv", totalGmv);
        } catch (Exception e) {
            summary.put("totalGmv", 0.0);
        }

        return summary;
    }
}
