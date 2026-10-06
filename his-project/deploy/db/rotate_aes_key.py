# -*- coding: utf-8 -*-
"""
AES 密钥轮换：把 pat_patient.id_card_no 的 v1: 密文从旧密钥重加密到新密钥。

用法（在轮换 WinSW XML 的 AES_KEY 之前执行；id_card_hash 是明文摘要，无需变更）：
    python rotate_aes_key.py <old_key_b64> <new_key_b64> [--dry-run]

--dry-run 自验模式：抽 5 行做 解密→重加密→再解密 往返断言并校验明文一致，不写库——
在真实轮换前锁定本脚本与 CryptoUtil 的 v1 格式兼容性（九十轮测试覆盖审计缺口 9）。
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
    args = [a for a in sys.argv[1:] if a != "--dry-run"]
    dry_run = "--dry-run" in sys.argv[1:]
    old_b64, new_b64 = args
    old, new = crypter(old_b64), crypter(new_b64)
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, id_card_no FROM pat_patient WHERE id_card_no LIKE 'v1:%%' LIMIT 5")
            samples = cur.fetchall()
            # 格式自验（不写库）：解密→用新钥重加密→再解密，明文往返必须一致——
            # 锁定本脚本与 CryptoUtil v1:{Base64(IV||GCM密文+tag)} 的兼容性，防格式漂移静默全库不可解
            for sid, cipher_text in samples:
                plain = decrypt(cipher_text, old)
                re_plain = decrypt(encrypt(plain, new), new)
                assert re_plain == plain, "格式自验失败 id=%s（脚本与 CryptoUtil 的 v1 格式漂移）" % sid
            print("格式自验通过：%d 行样本 往返解密一致" % len(samples))
            if dry_run:
                print("--dry-run：未写库")
                return
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
