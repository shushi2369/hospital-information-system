package com.his.infrastructure.util;

/**
 * 平台事件载荷的 JSON 字符串转义（bug 模式 12）：用户输入直拼进 recordEvent 的
 * JSON 字符串会破坏载荷结构（引号/反斜杠/换行），拼接前必须过此转义。
 * 一百二十六轮【10】P3：补齐制表符/退格/换页与其余 <0x20 控制字符，
 * 原 \r→空格是取巧——下游 JSON 函数/严格解析器会拒绝非法 JSON。
 */
public final class JsonEscapeUtil {

    private JsonEscapeUtil() {
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
