package com.minduc.happabi.service.auth;

import java.util.Map;

public interface IRealtimeMetricsService {
    Map<String, Double> getDailyGmvLast30Days();
}
