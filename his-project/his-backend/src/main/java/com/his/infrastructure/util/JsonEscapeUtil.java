package com.his.infrastructure.util;

/**
 * 平台事件载荷的 JSON 字符串转义（bug 模式 12）：用户输入直拼进 recordEvent 的
 * JSON 字符串会破坏载荷结构（引号/反斜杠/换行），拼接前必须过此转义。
 */
public final class JsonEscapeUtil {

    private JsonEscapeUtil() {
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", " ");
    }
}
