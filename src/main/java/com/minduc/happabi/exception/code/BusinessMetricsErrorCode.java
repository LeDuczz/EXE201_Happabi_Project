package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum BusinessMetricsErrorCode implements ServiceErrorCode {
    GET_DAILY_GMV_LAST_30_DAYS_ERROR_CODE(HttpStatus.INTERNAL_SERVER_ERROR, "Error to get Gmv last 30 days")
    ;


    HttpStatus httpStatus;
    String message;
}
