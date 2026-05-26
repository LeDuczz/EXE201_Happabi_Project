package com.minduc.happabi.service.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface IAuditSearchService {
    Page<Map<String, Object>> searchLogs(String searchTerm, Pageable pageable);
}
