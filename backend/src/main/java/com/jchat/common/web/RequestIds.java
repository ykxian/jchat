package com.jchat.common.web;

import org.slf4j.MDC;

public final class RequestIds {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private RequestIds() {
    }

    public static String getCurrentRequestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }
}
