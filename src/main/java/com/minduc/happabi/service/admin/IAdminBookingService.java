package com.minduc.happabi.service.admin;

import com.minduc.happabi.dto.request.admin.AdminBookingSearchCriteria;
import com.minduc.happabi.dto.response.admin.AdminBookingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IAdminBookingService {

    Page<AdminBookingResponse> getBookings(AdminBookingSearchCriteria criteria, Pageable pageable);

    AdminBookingResponse getBookingDetail(UUID bookingId);
}
