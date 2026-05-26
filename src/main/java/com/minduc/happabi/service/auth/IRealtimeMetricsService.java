package com.minduc.happabi.service.auth;

import java.util.Map;

public interface IRealtimeMetricsService {

    Map<String, Double> getDailyGmvLast30Days();

    Map<String, Long> getDailyBookingCountLast30Days();

    Map<String, Long> getUserGrowthLast30Days();

    Map<String, Long> getRoleDistribution();

    Map<String, Object> getDashboardSummary();

}
