package com.his.infrastructure.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 横切工具类单测：LikeEscapeUtil（LIKE 注入面）+ JsonEscapeUtil（127 轮控制字符加固）+
 * PageQuery（128 轮 pageNum 上限）。
 */
class UtilGuardsTest {

    // ---------------- LikeEscapeUtil ----------------

    @Test
    void likeEscape_percentEscaped() {
        assertEquals("\\%", LikeEscapeUtil.escape("%"));
    }

    @Test
    void likeEscape_underscoreEscaped() {
        assertEquals("\\_", LikeEscapeUtil.escape("_"));
    }

    @Test
    void likeEscape_backslashEscapedFirst() {
        // 反斜杠必须最先转义，否则后续转义会被二次转义（\\\\% 而非 \\\%）
        assertEquals("\\\\\\%", LikeEscapeUtil.escape("\\%"));
    }

    @Test
    void likeEscape_nullAndEmptyPassthrough() {
        assertNull(LikeEscapeUtil.escape(null));
        assertEquals("", LikeEscapeUtil.escape(""));
    }

    @Test
    void likeEscape_normalTextUnchanged() {
        assertEquals("张三", LikeEscapeUtil.escape("张三"));
    }

    // ---------------- JsonEscapeUtil（127 轮加固） ----------------

    @Test
    void jsonEscape_quotesAndBackslash() {
        assertEquals("a\\\"b", JsonEscapeUtil.escape("a\"b"));
        assertEquals("a\\\\b", JsonEscapeUtil.escape("a\\b"));
    }

    @Test
    void jsonEscape_newlineAndTab() {
        assertEquals("a\\nb", JsonEscapeUtil.escape("a\nb"));
        assertEquals("a\\tb", JsonEscapeUtil.escape("a\tb"));
    }

    @Test
    void jsonEscape_controlCharsAsUnicode() {
        // 0x01 控制字符必须转成 \\u0001 而非原样直拼（非法 JSON）
        assertEquals("a\\u0001b", JsonEscapeUtil.escape("a\u0001b"));
    }

    @Test
    void jsonEscape_nullReturnsEmpty() {
        assertEquals("", JsonEscapeUtil.escape(null));
    }

    @Test
    void jsonEscape_outputIsParseableJsonFragment() throws Exception {
        // 端到端验证：转义产物作为 JSON 字符串值可被严格解析器接受
        String raw = "含\"引号\"、\\反斜杠、\n换行、\u0002 控制符";
        String json = "{\"v\":\"" + JsonEscapeUtil.escape(raw) + "\"}";
        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertEquals(raw, node.get("v").asText(), "转义产物必须可往返解析还原原文");
    }

    // ---------------- PageQuery（128 轮 pageNum 上限） ----------------

    @Test
    void pageQuery_pageSizeClampedTo200() {
        com.his.common.PageQuery q = new com.his.common.PageQuery();
        q.setPageSize(99999);
        assertEquals(200, q.toPage().getSize());
        q.setPageSize(0);
        assertEquals(1, q.toPage().getSize());
    }

    @Test
    void pageQuery_pageNumClampedLowAndHigh() {
        com.his.common.PageQuery q = new com.his.common.PageQuery();
        q.setPageNum(-5);
        assertEquals(1, q.toPage().getCurrent());
        q.setPageNum(99999999);
        assertEquals(10_000, q.toPage().getCurrent(), "深翻页 OFFSET 上限 10000（128 轮）");
    }
}
