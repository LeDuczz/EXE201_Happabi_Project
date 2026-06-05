package com.minduc.happabi.service.admin;

import com.minduc.happabi.dto.request.admin.CreateDoctorAccountRequest;
import com.minduc.happabi.dto.response.admin.DoctorAccountResponse;

public interface IAdminDoctorAccountService {

    DoctorAccountResponse createDoctorAccount(CreateDoctorAccountRequest request);

}
