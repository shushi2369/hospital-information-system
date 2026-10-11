package com.his.infrastructure.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * 身份证号加密存储（《04》§6）：AES-256-GCM，密文格式 v1:{Base64(iv+ciphertext)}；
 * 另提供摘要用于唯一索引与检索（密文不可查）。
 * 一百一十四轮 S-02：唯一索引摘要从无盐 SHA-256 升级为 HMAC-SHA256（独立 pepper）——
 * 身份证号熵低（地区码+生日可枚举），无盐哈希在拖库场景可离线穷举还原明文。
 * 过渡期用 {@link #idCardHashCandidates} 双哈希查询兼容存量（新写入一律 HMAC）。
 */
@Component
public class CryptoUtil {
    private static final String PREFIX = "v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecretKeySpec pepperKey;
    private final SecureRandom random = new SecureRandom();

    public CryptoUtil(@Value("${his.crypto.aes-key}") String base64Key,
                      @Value("${his.crypto.hash-pepper:dev-only-hash-pepper-change-me-32bytes-ok}") String pepper) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES 密钥必须为 32 字节的 Base64 串");
        }
        // 仓库默认密钥（32 个 'A'）原样进生产 = 库中身份证密文形同明文（八十六轮安全审计）；
        // 轮换流程见 deploy/db/rotate_aes_key.py
        if ("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=".equals(base64Key)) {
            System.err.println("==================================================================");
            System.err.println("[SECURITY-WARN] AES_KEY 仍在使用仓库默认值：身份证密文可被任何拿到仓库的人解密。");
            System.err.println("  生产部署必须注入强随机密钥，存量密文用 deploy/db/rotate_aes_key.py 重加密。");
            System.err.println("==================================================================");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || !cipherText.startsWith(PREFIX)) {
            return cipherText;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] plain = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    public String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败", e);
        }
    }

    /** 一百一十四轮 S-02：HMAC-SHA256（独立 pepper）——新写入的 id_card_hash 统一用此算法 */
    public String hmacSha256Hex(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(pepperKey);
            byte[] digest = mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败", e);
        }
    }

    /** 过渡期查询候选：存量行仍为无盐 SHA-256，新行为 HMAC——按双候选 IN 查询兼容，
     *  启动补偿任务（PatientHashRehashTask）会把存量逐步重算为 HMAC，届时可移除 sha256 候选 */
    public List<String> idCardHashCandidates(String idCardNo) {
        return List.of(hmacSha256Hex(idCardNo), sha256Hex(idCardNo));
    }
}
