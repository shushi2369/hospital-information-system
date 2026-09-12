package com.his.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskUtilTest {

    @Test
    void maskIdCardKeepsHeadAndTail() {
        assertEquals("3401**********1234", MaskUtil.maskIdCard("340104199001011234"));
    }

    @Test
    void maskPhoneKeepsHeadAndTail() {
        assertEquals("138****5678", MaskUtil.maskPhone("13812345678"));
    }

    @Test
    void shortValuesFullyMasked() {
        assertEquals("***", MaskUtil.maskIdCard("123"));
        assertEquals("***", MaskUtil.maskPhone(null));
    }

    @Test
    void maskJsonHidesPasswordAndIdCard() {
        String in = "{\"username\":\"a\",\"password\":\"Secret@123\",\"idCardNo\":\"340104199001011234\"}";
        String out = MaskUtil.maskJson(in);
        assertTrue(out.contains("\"password\":\"***\""), out);
        assertTrue(out.contains("\"idCardNo\":\"***\""), out);
        assertTrue(out.contains("\"username\":\"a\""), out);
    }

    @Test
    void maskJsonTruncatesLongInput() {
        String out = MaskUtil.maskJson("x".repeat(5000));
        assertTrue(out.length() < 5100, String.valueOf(out.length()));
        assertTrue(out.endsWith("(truncated)"));
    }
}
