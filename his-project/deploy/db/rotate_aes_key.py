# -*- coding: utf-8 -*-
"""
AES 密钥轮换：把 pat_patient.id_card_no 的 v1: 密文从旧密钥重加密到新密钥。

用法（在轮换 WinSW XML 的 AES_KEY 之前执行；id_card_hash 是明文摘要，无需变更）：
    python rotate_aes_key.py <old_key_b64> <new_key_b64>

格式对应 CryptoUtil.java：v1:{Base64(12字节IV || AES-256-GCM密文+16字节tag)}。
轮换窗口内旧密钥服务无法解密新密文——脚本跑完立即改 XML 并重启后端。
"""
import base64
import sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

import pymysql

DB = dict(host="127.0.0.1", user="root", password="root123", database="his", charset="utf8mb4")
BATCH = 500


def crypter(key_b64: str) -> AESGCM:
    key = base64.b64decode(key_b64)
    assert len(key) == 32, "AES 密钥必须 32 字节"
    return AESGCM(key)


def decrypt(cipher_text: str, old: AESGCM) -> str:
    raw = base64.b64decode(cipher_text[3:])
    plain = old.decrypt(raw[:12], raw[12:], None)
    return plain.decode("utf-8")


def encrypt(plain: str, new: AESGCM) -> str:
    import os
    iv = os.urandom(12)
    body = new.encrypt(iv, plain.encode("utf-8"), None)
    return "v1:" + base64.b64encode(iv + body).decode()


def main():
    old_b64, new_b64 = sys.argv[1], sys.argv[2]
    old, new = crypter(old_b64), crypter(new_b64)
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, id_card_no FROM pat_patient WHERE id_card_no LIKE 'v1:%%'")
            rows = cur.fetchall()
            print("待重加密 %d 行" % len(rows))
            done = 0
            for start in range(0, len(rows), BATCH):
                chunk = rows[start:start + BATCH]
                updates = []
                for pid, cipher_text in chunk:
                    plain = decrypt(cipher_text, old)          # 旧密钥解不开会抛 InvalidTag——立即终止而非留半套
                    updates.append((encrypt(plain, new), pid))
                cur.executemany("UPDATE pat_patient SET id_card_no=%s WHERE id=%s", updates)
                conn.commit()
                done += len(updates)
                print("  %d / %d" % (done, len(rows)))
            # 抽样验证：新密文能被新密钥解开
            cur.execute("SELECT id_card_no FROM pat_patient WHERE id_card_no LIKE 'v1:%%' LIMIT 5")
            for (c,) in cur.fetchall():
                decrypt(c, new)
            print("抽样验证通过（新密钥解密 OK）")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
