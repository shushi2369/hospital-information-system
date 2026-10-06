# -*- coding: utf-8 -*-
"""
测试数据一次性清理（九十轮数据生命周期审计 P0 落地）。

背景：53,749 名患者中 53,747 名是 43+ 轮 e2e/压测残留（压测 51,892、链路/验收/查验等 ~1,855），
唯一保留 = 种子患者张三(id=1)/李四(id=2)。压测数据绑架患者搜索上下文、报表基数与备份体积（58%）。

用法：
    python cleanup_test_data.py            # dry-run：只打印各表将删除的行数
    python cleanup_test_data.py --apply    # 实际删除

前置：已执行 mysqldump 全量备份（his_pre_cleanup_*.sql，Dump completed 校验）。
删除顺序 = 引用图逆序（子表 → 中间单 → 患者主表）；外键缺失架构下漏一张表
会留孤儿行——删完必须跑 deploy/db/consistency_check.sql（75 段全过才算干净）。
"""
import sys
import pymysql

DB = dict(host="127.0.0.1", user="root", password="root123", database="his", charset="utf8mb4")
KEEP_IDS = (1, 2)  # 种子患者张三/李四

# (表, 键列, 引用的临时表)：从叶子到根排序
STEPS = [
    # ---- 叶子：挂中间单的子表 ----
    ("bil_charge_detail",   "bill_id",        "bills"),
    ("bil_payment_record",  "bill_id",        "bills"),
    ("bil_refund_detail",   "refund_bill_id", "refunds"),
    ("medins_settle",       "bill_id",        "bills"),
    ("doc_order_exec",      "order_id",       "orders"),
    ("doc_order_item",      "order_id",       "orders"),
    ("cdss_hit",            "order_id",       "orders"),
    ("cli_prescription_item", "prescription_id", "rx"),
    ("phr_review_record",   "prescription_id", "rx"),
    ("lis_specimen",        "request_id",     "lis"),
    ("lis_result",          "request_id",     "lis"),
    ("lis_report",          "request_id",     "lis"),
    ("ris_appointment",     "request_id",     "ris"),
    ("ris_image",           "request_id",     "ris"),
    ("ris_report",          "request_id",     "ris"),
    ("or_schedule",         "request_id",     "or_req"),
    ("or_anesthesia_record", "request_id",    "or_req"),
    ("or_check_record",     "request_id",     "or_req"),
    ("or_postop_record",    "request_id",     "or_req"),
    ("bb_cross_match",      "request_id",     "bb"),
    ("bb_issue",            "request_id",     "bb"),
    ("bb_transfusion",      "request_id",     "bb"),
    ("bb_adverse",          "request_id",     "bb"),
    ("pe_result",           "record_id",      "pe"),
    ("pe_report",           "record_id",      "pe"),
    ("mrc_homepage",        "admission_id",   "adms"),
    ("emr_record",          "admission_id",   "adms"),
    ("inp_daily_fee",       "admission_id",   "adms"),
    ("inp_deposit",         "admission_id",   "adms"),
    ("inp_transfer",        "admission_id",   "adms"),
    ("nur_vital_sign",      "admission_id",   "adms"),
    ("alert_critical",      "admission_id",   "adms"),
    ("cli_diagnosis",       "visit_id",       "visits"),
    ("cli_medical_order",   "visit_id",       "visits"),
    ("phr_dispense_order",  "prescription_id", "rx"),
    ("phr_return_order",    "prescription_id", "rx"),
    # ---- 中间单本身 ----
    ("phr_dispense_order",  "patient_id",     "pat"),
    ("phr_return_order",    "patient_id",     "pat"),
    ("bil_charge_detail",   "patient_id",     "pat"),
    ("reg_registration",    "patient_id",     "pat"),
    ("cli_visit",           "patient_id",     "pat"),
    ("cli_prescription",    "patient_id",     "pat"),
    ("cli_exam_application", "patient_id",    "pat"),
    ("doc_order",           "patient_id",     "pat"),
    ("lis_request",         "patient_id",     "pat"),
    ("ris_request",         "patient_id",     "pat"),
    ("or_surgery_request",  "patient_id",     "pat"),
    ("bb_request",          "patient_id",     "pat"),
    ("emc_visit",           "patient_id",     "pat"),
    ("emc_triage",          "patient_id",     "pat"),
    ("pe_record",           "patient_id",     "pat"),
    ("mrc_record",          "patient_id",     "pat"),
    ("cnt_request",         "patient_id",     "pat"),
    ("pub_infectious_card", "patient_id",     "pat"),
    ("pub_hai_case",        "patient_id",     "pat"),
    ("bil_charge_bill",     "patient_id",     "pat"),
    ("bil_refund_bill",     "patient_id",     "pat"),
    ("alert_critical",      "patient_id",     "pat"),
    ("nur_vital_sign",      "patient_id",     "pat"),
    ("pat_medical_card",    "patient_id",     "pat"),
    ("plt_id_map",          "source_id",      "pat"),
    ("plt_master_index",    "patient_id",     "pat"),
    ("inp_admission",       "patient_id",     "pat"),
    # ---- 根 ----
    ("pat_patient",         "id",             "pat"),
]

TEMP_SQL = {
    "pat":     "SELECT id FROM pat_patient WHERE id NOT IN {keep}",
    "visits":  "SELECT v.id FROM cli_visit v JOIN tmp_pat t ON v.patient_id = t.id",
    "adms":    "SELECT a.id FROM inp_admission a JOIN tmp_pat t ON a.patient_id = t.id",
    "bills":   "SELECT b.id FROM bil_charge_bill b JOIN tmp_pat t ON b.patient_id = t.id",
    "refunds": "SELECT r.id FROM bil_refund_bill r JOIN tmp_pat t ON r.patient_id = t.id",
    "rx":      "SELECT p.id FROM cli_prescription p JOIN tmp_pat t ON p.patient_id = t.id",
    "orders":  "SELECT o.id FROM doc_order o JOIN tmp_pat t ON o.patient_id = t.id",
    "lis":     "SELECT l.id FROM lis_request l JOIN tmp_pat t ON l.patient_id = t.id",
    "ris":     "SELECT r.id FROM ris_request r JOIN tmp_pat t ON r.patient_id = t.id",
    "or_req":  "SELECT o.id FROM or_surgery_request o JOIN tmp_pat t ON o.patient_id = t.id",
    "bb":      "SELECT b.id FROM bb_request b JOIN tmp_pat t ON b.patient_id = t.id",
    "pe":      "SELECT p.id FROM pe_record p JOIN tmp_pat t ON p.patient_id = t.id",
}


def main():
    apply_mode = "--apply" in sys.argv[1:]
    conn = pymysql.connect(**DB)
    keep = str(KEEP_IDS)
    try:
        with conn.cursor() as cur:
            cur.execute("DROP TEMPORARY TABLE IF EXISTS tmp_pat")
            cur.execute("CREATE TEMPORARY TABLE tmp_pat (id BIGINT PRIMARY KEY) "
                        + TEMP_SQL["pat"].format(keep=keep))
            cur.execute("SELECT COUNT(*) FROM tmp_pat")
            doomed = cur.fetchone()[0]
            print("待清理患者 %d 名（保留 %s）" % (doomed, keep))
            # 建全部中间临时表
            for name in TEMP_SQL:
                if name == "pat":
                    continue
                cur.execute("DROP TEMPORARY TABLE IF EXISTS tmp_" + name)
                cur.execute("CREATE TEMPORARY TABLE tmp_%s (id BIGINT PRIMARY KEY) %s"
                            % (name, TEMP_SQL[name]))
            total = 0
            for table, key, src in STEPS:
                if apply_mode:
                    cur.execute("DELETE FROM %s WHERE %s IN (SELECT id FROM tmp_%s)" % (table, key, src))
                    n = cur.rowcount
                    conn.commit()
                    total += n
                    print("  %-22s -%-7d" % (table, n))
                else:
                    # dry-run 只读估算：COUNT 而非 DELETE（自查审计 P0——原实现 commit 无条件执行，
                    # "dry-run" 实际删库且 rollback 无效）
                    cur.execute("SELECT COUNT(*) FROM %s WHERE %s IN (SELECT id FROM tmp_%s)" % (table, key, src))
                    n = cur.fetchone()[0]
                    total += n
                    if n:
                        print("  %-22s ~%-7d (dry-run)" % (table, n))
            print("合计 %d 行 %s" % (total, "已删除" if apply_mode else "（dry-run 未执行任何删除）"))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
