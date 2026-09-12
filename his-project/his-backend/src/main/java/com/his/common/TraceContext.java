package com.his.common;

import org.slf4j.MDC;

/**
 * 请求链路追踪 ID（TraceIdFilter 写入，日志与响应体贯穿）
 */
public final class TraceContext {
    public static final String KEY = "traceId";

    private TraceContext() {
    }

    public static String traceId() {
        return MDC.get(KEY);
    }

    public static void set(String traceId) {
        MDC.put(KEY, traceId);
    }

    public static void clear() {
        MDC.remove(KEY);
    }
}
