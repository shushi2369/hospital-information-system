package com.his.common;

import lombok.Getter;
import lombok.Setter;

/**
 * 统一响应信封：{"code":"OK","message":"成功","data":{},"traceId":"..."}
 */
@Getter
@Setter
public class R<T> {
    private String code;
    private String message;
    private T data;
    private String traceId;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.OK.getCode();
        r.message = ErrorCode.OK.getMessage();
        r.data = data;
        r.traceId = TraceContext.traceId();
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        R<T> r = new R<>();
        r.code = errorCode.getCode();
        r.message = message;
        r.traceId = TraceContext.traceId();
        return r;
    }
}
