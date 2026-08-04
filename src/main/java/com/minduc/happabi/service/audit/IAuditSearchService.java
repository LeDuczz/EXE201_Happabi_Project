package com.minduc.happabi.service.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface IAuditSearchService {
    Page<Map<String, Object>> searchLogs(String searchTerm, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    List<String> suggestSearchTerms(String searchTerm);
}
