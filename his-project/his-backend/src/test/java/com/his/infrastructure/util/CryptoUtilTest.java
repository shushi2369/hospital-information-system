package com.his.infrastructure.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoUtilTest {

    private CryptoUtil cryptoUtil;

    @BeforeEach
    void setUp() {
        // 32 字节测试密钥（Base64）
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }
        cryptoUtil = new CryptoUtil(Base64.getEncoder().encodeToString(key), "test-pepper");
    }

    @Test
    void encryptDecryptRoundTrip() {
        String idCard = "340104199001011234";
        String cipher = cryptoUtil.encrypt(idCard);
        assertTrue(cipher.startsWith("v1:"), cipher);
        assertEquals(idCard, cryptoUtil.decrypt(cipher));
    }

    @Test
    void sameInputDifferentCipherText() {
        // GCM 随机 IV：同明文两次密文不同，但都可解回
        String plain = "340104199001011234";
        assertNotEquals(cryptoUtil.encrypt(plain), cryptoUtil.encrypt(plain));
    }

    @Test
    void decryptPlainTextPassthrough() {
        // 兼容历史明文数据（无 v1: 前缀）原样返回
        assertEquals("legacy-plain", cryptoUtil.decrypt("legacy-plain"));
    }

    @Test
    void sha256HexIsStable64Hex() {
        String hash = cryptoUtil.sha256Hex("340104199001011234");
        assertEquals(64, hash.length());
        assertEquals(hash, cryptoUtil.sha256Hex("340104199001011234"));
        assertNotEquals(hash, cryptoUtil.sha256Hex("340104199001011235"));
    }

    @Test
    void rejectsWrongKeyLength() {
        String bad = Base64.getEncoder().encodeToString(new byte[16]);
        try {
            new CryptoUtil(bad, "test-pepper");
            throw new AssertionError("应拒绝非 32 字节密钥");
        } catch (IllegalArgumentException expected) {
            // 预期路径
        }
    }

    /** 一百一十九轮覆盖盲区【4】：HMAC 与 SHA-256 双算法候选（S-02 过渡期兼容的根基） */
    @Test
    void hmacSha256HexIsStableAndDiffersFromSha256() {
        String idCard = "340104199001011234";
        String hmac = cryptoUtil.hmacSha256Hex(idCard);
        assertEquals(64, hmac.length());
        assertEquals(hmac, cryptoUtil.hmacSha256Hex(idCard));
        // 同 pepper 下 HMAC 稳定且与无盐 SHA-256 必不相同（否则双候选退化）
        assertNotEquals(hmac, cryptoUtil.sha256Hex(idCard));
    }

    @Test
    void hmacDiffersAcrossPeppers() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }
        CryptoUtil otherPepper = new CryptoUtil(Base64.getEncoder().encodeToString(key), "another-pepper");
        String idCard = "340104199001011234";
        // pepper 参与摘要：不同 pepper 对同一证件号产出不同哈希（撞库需逐 pepper 重算）
        assertNotEquals(cryptoUtil.hmacSha256Hex(idCard), otherPepper.hmacSha256Hex(idCard));
    }

    @Test
    void idCardHashCandidatesContainsBothAlgorithms() {
        String idCard = "340104199001011234";
        var candidates = cryptoUtil.idCardHashCandidates(idCard);
        assertEquals(2, candidates.size());
        assertTrue(candidates.contains(cryptoUtil.hmacSha256Hex(idCard)));
        assertTrue(candidates.contains(cryptoUtil.sha256Hex(idCard)));
    }
}
