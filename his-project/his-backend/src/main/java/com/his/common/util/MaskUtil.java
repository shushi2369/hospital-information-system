package com.his.common.util;

/**
 * 敏感数据脱敏（《04 权限与安全设计》§6）：展示与日志统一出口。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return "***";
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 审计日志参数 JSON 打码：password/idCardNo/phone 等字段 */
    public static String maskJson(String json) {
        if (json == null) {
            return null;
        }
        if (json.length() > 2000) {
            json = json.substring(0, 2000) + "...(truncated)";
        }
        return json
                .replaceAll("(\"(password|pwd|newPassword|oldPassword)\"\\s*:\\s*\")(.*?)(\")", "$1***$4")
                .replaceAll("(\"(idCardNo|idCard)\"\\s*:\\s*\")(.*?)(\")", "$1***$4")
                .replaceAll("(\"(phone|mobile)\"\\s*:\\s*\")(\\d{3})\\d{4}(\\d{4}\")", "$1$3****$4");
    }
}
