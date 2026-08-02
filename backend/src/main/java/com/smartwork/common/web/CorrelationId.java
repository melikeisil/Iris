package com.smartwork.common.web;

import org.slf4j.MDC;

/**
 * One ID follows a request from ticket creation through AI analysis, recommendation,
 * approval and Jira synchronization. It lives in the MDC so every log line carries it,
 * and it is stamped onto outbox events and audit rows.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "-" : value;
    }

    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
