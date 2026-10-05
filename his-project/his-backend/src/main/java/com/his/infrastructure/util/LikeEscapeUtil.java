package com.his.infrastructure.util;

/**
 * LIKE 通配符转义：用户输入直接进 .like()/.likeRight() 时，% 和 _ 会被当作通配符——
 * 输入 "%" 使前缀检索退化为全表扫（审计/登录日志是最大 append-only 表），语义上变成"匹配所有人"。
 * 配合 MyBatis-Plus 默认 ESCAPE '\'：转义 \ % _ 三个字符即可。
 */
public final class LikeEscapeUtil {

    private LikeEscapeUtil() {
    }

    public static String escape(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
