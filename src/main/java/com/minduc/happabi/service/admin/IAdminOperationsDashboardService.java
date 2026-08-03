package com.minduc.happabi.service.admin;

import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse;

import java.time.LocalDate;

public interface IAdminOperationsDashboardService {

    AdminOperationsDashboardResponse getOverview(LocalDate from, LocalDate to);
}
