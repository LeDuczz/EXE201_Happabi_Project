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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RealtimeMetricsService implements IRealtimeMetricsService {

    private final ElasticsearchClient elasticsearchClient;

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
                                    .must(m -> m.range(r -> r.field("timestamp").gte(JsonData.fromJson(thirtyDays.toString()))))))
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
            throw new AppException(BusinessMetricsErrorCode.GET_DAILY_GMV_LAST_30_DAYS_ERROR_CODE);
        }


        return gmvByDate;

    }
}
