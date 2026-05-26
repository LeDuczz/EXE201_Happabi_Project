package com.minduc.happabi.repository;

import com.minduc.happabi.observability.document.BusinessMetricDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BusinessMetricRepository extends ElasticsearchRepository<BusinessMetricDocument, String> {
}
