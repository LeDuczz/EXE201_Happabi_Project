package com.minduc.happabi.observability.support;

import com.minduc.happabi.common.utils.NetworkUtils;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RequestContextUtil {

    public String getClientIp() {
        return ObservationUtils.currentRequest()
                .map(NetworkUtils::resolveClientIp)
                .orElse(null);
    }

    public String getUserAgent() {
        return ObservationUtils.currentRequest()
                .map(request -> request.getHeader("User-Agent"))
                .orElse(null);
    }

}
