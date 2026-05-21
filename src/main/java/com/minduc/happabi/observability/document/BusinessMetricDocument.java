package com.minduc.happabi.observability.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "happabi-business-metrics")
public class BusinessMetricDocument {
    @Id
    private String eventId;

    @Field(type = FieldType.Keyword)
    private String eventType; // "TRANSACTION_SUCCESS", "BOOKING_COMPLETED"

    @Field(type = FieldType.Date)
    private Instant timestamp;

    @Field(type = FieldType.Double)
    private BigDecimal amount; // GMV

    @Field(type = FieldType.Keyword)
    private String role; // "MOTHER", "NURSE"

    @Field(type = FieldType.Keyword)
    private String status;


}
