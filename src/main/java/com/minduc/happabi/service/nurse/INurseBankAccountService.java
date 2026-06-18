package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.request.nurse.UpsertNurseBankAccountRequest;
import com.minduc.happabi.dto.response.nurse.NurseBankAccountResponse;

public interface INurseBankAccountService {
    NurseBankAccountResponse getMyBankAccount();

    NurseBankAccountResponse upsertMyBankAccount(UpsertNurseBankAccountRequest request);
}
