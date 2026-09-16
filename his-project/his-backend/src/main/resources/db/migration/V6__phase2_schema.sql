-- =====================================================================
-- HIS V6 二期全量新表 + 一期表改造（《09-二期数据库设计》V1.0）
-- =====================================================================

-- ----------------------------
-- 一期表改造
-- ----------------------------
ALTER TABLE bil_charge_bill
  ADD COLUMN admission_id BIGINT UNSIGNED NULL COMMENT '住院结算来源' AFTER visit_id,
  ADD UNIQUE KEY uk_bill_adm (admission_id);
ALTER TABLE bil_charge_detail
  ADD COLUMN admission_id BIGINT UNSIGNED NULL COMMENT '住院来源' AFTER visit_id,
  ADD KEY idx_cd_adm (admission_id);
ALTER TABLE cli_exam_application
  MODIFY visit_id BIGINT UNSIGNED NULL COMMENT '门诊来源(住院时为空)',
  ADD COLUMN admission_id BIGINT UNSIGNED NULL COMMENT '住院来源' AFTER visit_id,
  ADD KEY idx_apply_adm (admission_id);

-- ----------------------------
-- 平台层 plt_
-- ----------------------------
CREATE TABLE plt_master_index (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mpi_no      VARCHAR(32)     NOT NULL COMMENT '主索引号(前缀M)',
    patient_id  BIGINT UNSIGNED NOT NULL COMMENT '一期 pat_patient.id',
    merge_flag  TINYINT         NOT NULL DEFAULT 0 COMMENT '1已合并(预留)',
    merged_into BIGINT UNSIGNED NULL COMMENT '合并目标mpi_id(预留)',
    status      TINYINT         NOT NULL DEFAULT 1,
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mpi_no (mpi_no),
    UNIQUE KEY uk_mpi_patient (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '患者主索引';

CREATE TABLE plt_id_map (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mpi_id        BIGINT UNSIGNED NOT NULL COMMENT '主索引',
    source_system VARCHAR(32)     NOT NULL COMMENT '来源系统',
    source_id     VARCHAR(64)     NOT NULL COMMENT '来源标识',
    status        TINYINT         NOT NULL DEFAULT 1,
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_id_map (mpi_id, source_system, source_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EMPI来源映射';

CREATE TABLE plt_event_log (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_no   VARCHAR(32)     NOT NULL COMMENT '事件号(前缀EV)',
    event_type VARCHAR(64)     NOT NULL COMMENT '事件类型(事件目录)',
    biz_no     VARCHAR(32)     NOT NULL COMMENT '业务单号',
    payload    JSON            NULL COMMENT '事件载荷',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_event_type (event_type, created_at),
    KEY idx_event_biz (biz_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '集成事件日志(outbox,append-only)';

-- ----------------------------
-- 住院管理 inp_
-- ----------------------------
CREATE TABLE inp_ward (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ward_code  VARCHAR(32)     NOT NULL COMMENT '病区编码',
    ward_name  VARCHAR(64)     NOT NULL COMMENT '病区名称',
    dept_id    BIGINT UNSIGNED NOT NULL COMMENT '关联临床科室',
    location   VARCHAR(64)     NULL,
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ward_code (ward_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '病区';

CREATE TABLE inp_bed (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ward_id              BIGINT UNSIGNED NOT NULL COMMENT '病区',
    bed_no               VARCHAR(16)     NOT NULL COMMENT '床号',
    bed_status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1空闲 2占用 3预约 4停用',
    current_admission_id BIGINT UNSIGNED NULL COMMENT '当前住院',
    charge_item_id       BIGINT UNSIGNED NOT NULL COMMENT '床位费收费项目',
    status               TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0删除',
    created_by           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version              INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bed_ward (ward_id, bed_no),
    KEY idx_bed_status (bed_status, ward_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '床位';

CREATE TABLE inp_admission (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admission_no       VARCHAR(32)     NOT NULL COMMENT '住院号(前缀ZY)',
    patient_id         BIGINT UNSIGNED NOT NULL COMMENT '患者',
    dept_id            BIGINT UNSIGNED NOT NULL COMMENT '现科室',
    ward_id            BIGINT UNSIGNED NOT NULL COMMENT '现病区',
    bed_id             BIGINT UNSIGNED NOT NULL COMMENT '现床位',
    doctor_id          BIGINT UNSIGNED NOT NULL COMMENT '主治医生',
    admission_type     TINYINT         NOT NULL DEFAULT 1 COMMENT '1普通 2急诊 3转院入院',
    admission_time     DATETIME        NOT NULL COMMENT '入院时间',
    planned_diagnosis  VARCHAR(128)    NULL COMMENT '入院诊断',
    deposit_total      DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '押金累计',
    discharge_way      TINYINT         NULL COMMENT '1治愈 2好转 3未愈 4死亡 5自动离院 6转院',
    discharge_diagnosis VARCHAR(256)   NULL COMMENT '出院诊断',
    discharge_time     DATETIME        NULL COMMENT '出院时间',
    status             TINYINT         NOT NULL DEFAULT 10 COMMENT '10在院 20出院未结 30已结算 40转院',
    created_by         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version            INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admission_no (admission_no),
    KEY idx_adm_patient (patient_id, status),
    KEY idx_adm_status (status, dept_id),
    KEY idx_adm_bed (bed_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '住院记录';

CREATE TABLE inp_transfer (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admission_id BIGINT UNSIGNED NOT NULL COMMENT '住院',
    from_dept_id BIGINT UNSIGNED NOT NULL COMMENT '转出科室',
    to_dept_id   BIGINT UNSIGNED NOT NULL COMMENT '转入科室',
    from_ward_id BIGINT UNSIGNED NOT NULL,
    to_ward_id   BIGINT UNSIGNED NOT NULL,
    transfer_time DATETIME       NOT NULL COMMENT '转科时间',
    reason       VARCHAR(256)    NULL COMMENT '转科原因',
    status       TINYINT         NOT NULL DEFAULT 1,
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_transfer_adm (admission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '转科记录';

CREATE TABLE inp_deposit (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admission_id BIGINT UNSIGNED NOT NULL COMMENT '住院',
    amount       DECIMAL(12, 2)  NOT NULL COMMENT '押金金额',
    pay_method   TINYINT         NOT NULL COMMENT '1现金 2银行卡 3微信 4支付宝(模拟)',
    pay_time     DATETIME        NOT NULL COMMENT '缴纳时间',
    operator_id  BIGINT UNSIGNED NOT NULL COMMENT '经办人',
    status       TINYINT         NOT NULL DEFAULT 1,
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_deposit_adm (admission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '押金(只增不减)';

CREATE TABLE inp_daily_fee (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admission_id     BIGINT UNSIGNED NOT NULL COMMENT '住院',
    fee_date         DATE            NOT NULL COMMENT '费用日期',
    fee_type         TINYINT         NOT NULL COMMENT '费用类别(同收费项目category)',
    source_type      TINYINT         NOT NULL COMMENT '1床位日费 2医嘱执行 3摆药出库 4手记',
    source_detail_id BIGINT UNSIGNED NOT NULL COMMENT '来源行ID',
    item_name        VARCHAR(64)     NOT NULL COMMENT '项目名快照',
    quantity         DECIMAL(12, 2)  NOT NULL,
    unit_price       DECIMAL(10, 2)  NOT NULL,
    amount           DECIMAL(10, 2)  NOT NULL,
    charge_status    TINYINT         NOT NULL DEFAULT 0 COMMENT '0未结算 1已结算',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0作废',
    created_by       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version          INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_df_adm_date (admission_id, fee_date),
    KEY idx_df_adm_charge (admission_id, charge_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '住院每日费用(一日清)';

-- ----------------------------
-- 医嘱闭环住院段 doc_
-- ----------------------------
CREATE TABLE doc_order (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no       VARCHAR(32)     NOT NULL COMMENT '医嘱号(前缀YZ)',
    admission_id   BIGINT UNSIGNED NOT NULL COMMENT '住院',
    patient_id     BIGINT UNSIGNED NOT NULL COMMENT '患者',
    doctor_id      BIGINT UNSIGNED NOT NULL COMMENT '开立医生',
    order_class    TINYINT         NOT NULL COMMENT '1长期 2临时',
    category       TINYINT         NOT NULL COMMENT '1药品 2检查 3检验 4治疗 5护理 6材料',
    frequency      VARCHAR(16)     NULL COMMENT '频次 qd/bid/tid/q8h/prn',
    start_time     DATETIME        NULL COMMENT '开始时间',
    stop_time      DATETIME        NULL COMMENT '停止时间',
    skin_test_flag TINYINT         NOT NULL DEFAULT 0 COMMENT '1需皮试',
    total_amount   DECIMAL(10, 2)  NOT NULL DEFAULT 0 COMMENT '合计(后端重算)',
    review_by      BIGINT UNSIGNED NULL,
    review_at      DATETIME        NULL,
    review_comment VARCHAR(256)    NULL,
    stop_reason    VARCHAR(256)    NULL COMMENT '停止原因',
    void_reason    VARCHAR(256)    NULL COMMENT '作废原因',
    status         TINYINT         NOT NULL DEFAULT 10 COMMENT '10待审核 20审核通过 30执行中 40已执行 50已停止 60已作废 70已驳回',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_adm (admission_id, status),
    KEY idx_order_review (status, category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '住院医嘱';

CREATE TABLE doc_order_item (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id        BIGINT UNSIGNED NOT NULL COMMENT '医嘱',
    drug_id         BIGINT UNSIGNED NULL COMMENT '药品(药品类)',
    charge_item_id  BIGINT UNSIGNED NULL COMMENT '收费项目(非药品类)',
    item_name       VARCHAR(64)     NOT NULL COMMENT '名称快照',
    spec            VARCHAR(64)     NULL COMMENT '规格快照',
    dosage          VARCHAR(32)     NULL COMMENT '单次剂量',
    frequency       VARCHAR(16)     NULL,
    usage_route     VARCHAR(16)     NULL,
    days            INT             NULL,
    quantity        DECIMAL(12, 2)  NOT NULL,
    unit            VARCHAR(16)     NULL,
    unit_price      DECIMAL(10, 2)  NOT NULL,
    amount          DECIMAL(10, 2)  NOT NULL,
    usage_note      VARCHAR(128)    NULL,
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0作废',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_oi_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '医嘱明细';

CREATE TABLE doc_order_exec (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id         BIGINT UNSIGNED NOT NULL COMMENT '医嘱',
    item_id          BIGINT UNSIGNED NOT NULL COMMENT '明细行',
    exec_date        DATE            NOT NULL COMMENT '执行日期',
    exec_slot        VARCHAR(16)     NOT NULL COMMENT '执行时段(频次展开)',
    exec_type        TINYINT         NOT NULL COMMENT '1核对 2执行 3皮试',
    nurse_id         BIGINT UNSIGNED NULL COMMENT '执行护士',
    bed_no           VARCHAR(16)     NULL COMMENT '床号快照',
    result           VARCHAR(128)    NULL COMMENT '皮试结果/备注',
    charge_detail_id BIGINT UNSIGNED NULL COMMENT '计费回写',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1待执行 2已执行 3跳过',
    created_by       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version          INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exec (item_id, exec_date, exec_slot, exec_type),
    KEY idx_exec_nurse (nurse_id, exec_date, status),
    KEY idx_exec_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '医嘱执行记录';

-- ----------------------------
-- EMR emr_
-- ----------------------------
CREATE TABLE emr_record (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_no    VARCHAR(32)     NOT NULL COMMENT '文书号(前缀BW)',
    admission_id BIGINT UNSIGNED NOT NULL COMMENT '住院',
    doc_type     TINYINT         NOT NULL COMMENT '1入院记录 2病程记录 3出院记录 4知情同意 9其他',
    title        VARCHAR(64)     NOT NULL COMMENT '标题',
    content_json JSON            NULL COMMENT '结构化节',
    doctor_id    BIGINT UNSIGNED NOT NULL COMMENT '书写医生',
    record_time  DATETIME        NULL COMMENT '书写时间',
    qc_by        BIGINT UNSIGNED NULL COMMENT '质控人',
    qc_time      DATETIME        NULL COMMENT '质控时间',
    qc_issues    JSON            NULL COMMENT '问题清单',
    status       TINYINT         NOT NULL DEFAULT 10 COMMENT '10书写中 20已提交 30质控通过 40质控退回',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_no (record_no),
    KEY idx_record_adm (admission_id, doc_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '病历文书';

CREATE TABLE emr_template (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_name VARCHAR(64)    NOT NULL COMMENT '模板名',
    doc_type     TINYINT         NOT NULL COMMENT '文书类型',
    dept_id      BIGINT UNSIGNED NULL COMMENT '科室(空=全院)',
    content_json JSON            NULL COMMENT '模板结构',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tpl_type (doc_type, dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'EMR模板';

-- ----------------------------
-- 护理 nur_
-- ----------------------------
CREATE TABLE nur_schedule (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nurse_id   BIGINT UNSIGNED NOT NULL COMMENT '护士',
    ward_id    BIGINT UNSIGNED NOT NULL COMMENT '病区',
    shift_date DATE            NOT NULL COMMENT '排班日期',
    shift_type TINYINT         NOT NULL COMMENT '1白班 2中班 3夜班',
    status     TINYINT         NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_nurse_date (nurse_id, shift_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '护理排班';

CREATE TABLE nur_vital_sign (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admission_id BIGINT UNSIGNED NOT NULL COMMENT '住院',
    patient_id   BIGINT UNSIGNED NOT NULL COMMENT '患者',
    record_time  DATETIME        NOT NULL COMMENT '记录时间',
    temperature  DECIMAL(4, 1)   NOT NULL COMMENT '体温(30~42)',
    pulse        INT             NOT NULL COMMENT '脉搏(30~250)',
    respiration  INT             NOT NULL COMMENT '呼吸(5~60)',
    bp_high      INT             NOT NULL COMMENT '收缩压(40~260)',
    bp_low       INT             NOT NULL COMMENT '舒张压(20~180)',
    spo2         INT             NULL COMMENT '血氧(50~100)',
    pain_score   INT             NULL COMMENT '疼痛评分(0~10)',
    nurse_id     BIGINT UNSIGNED NOT NULL COMMENT '录入护士',
    status       TINYINT         NOT NULL DEFAULT 1,
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_vs_adm (admission_id, record_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '体征(体温单)';

-- ----------------------------
-- 病案 mrc_
-- ----------------------------
CREATE TABLE mrc_record (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mrc_no         VARCHAR(32)     NOT NULL COMMENT '病案号(=住院号)',
    admission_id   BIGINT UNSIGNED NOT NULL COMMENT '住院',
    patient_id     BIGINT UNSIGNED NOT NULL COMMENT '患者',
    archive_status TINYINT         NOT NULL DEFAULT 10 COMMENT '10待归档 20已归档 30借阅中 40已归还',
    qc_status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0未质控 1通过 2退回',
    qc_by          BIGINT UNSIGNED NULL,
    qc_time        DATETIME        NULL,
    archive_time   DATETIME        NULL COMMENT '归档时间',
    status         TINYINT         NOT NULL DEFAULT 1,
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mrc_adm (admission_id),
    UNIQUE KEY uk_mrc_no (mrc_no),
    KEY idx_mrc_status (archive_status, qc_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '病案';

CREATE TABLE mrc_homepage (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admission_id        BIGINT UNSIGNED NOT NULL COMMENT '住院',
    main_diagnosis_code VARCHAR(16)     NULL COMMENT '主要诊断ICD-10',
    main_diagnosis_name VARCHAR(64)     NULL,
    other_diagnoses     JSON            NULL COMMENT '其他诊断[{code,name}]',
    operation_code      VARCHAR(16)     NULL COMMENT '手术编码ICD-9-CM-3',
    charge_summary      JSON            NULL COMMENT '费用分类汇总',
    coder_id            BIGINT UNSIGNED NULL COMMENT '编码员',
    code_time           DATETIME        NULL,
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '1已编码',
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_homepage_adm (admission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '病案首页';

CREATE TABLE mrc_borrow (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mrc_id       BIGINT UNSIGNED NOT NULL COMMENT '病案',
    borrower_id  BIGINT UNSIGNED NOT NULL COMMENT '借阅人',
    borrow_time  DATETIME        NOT NULL COMMENT '借出时间',
    expect_return_time DATE     NULL COMMENT '预计归还',
    return_time  DATETIME        NULL COMMENT '实际归还',
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1借阅中 2已归还',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_borrow_mrc (mrc_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '病案借阅';

CREATE TABLE mrc_icd10 (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code       VARCHAR(16)     NOT NULL COMMENT 'ICD-10 编码',
    name       VARCHAR(64)     NOT NULL COMMENT '诊断名称',
    category   VARCHAR(32)     NULL COMMENT '章节',
    status     TINYINT         NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_icd_code (code),
    KEY idx_icd_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ICD-10 字典';

-- ----------------------------
-- 医保 medins_
-- ----------------------------
CREATE TABLE medins_settle (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    settle_no      VARCHAR(32)     NOT NULL COMMENT '申报号(前缀YB)',
    bill_id        BIGINT UNSIGNED NOT NULL COMMENT '出院结算账单',
    admission_id   BIGINT UNSIGNED NOT NULL COMMENT '住院',
    insurance_type TINYINT         NOT NULL COMMENT '1城镇职工 2城乡居民',
    total_amount   DECIMAL(12, 2)  NOT NULL COMMENT '费用总额',
    account_pay    DECIMAL(12, 2)  NOT NULL COMMENT '个人账户支付(Mock按比例)',
    pool_pay       DECIMAL(12, 2)  NOT NULL COMMENT '统筹支付(Mock按比例)',
    self_pay       DECIMAL(12, 2)  NOT NULL COMMENT '自费',
    apply_time     DATETIME        NOT NULL COMMENT '申报时间',
    reconcile_time DATETIME        NULL COMMENT '对账时间',
    diff_reason    VARCHAR(256)    NULL COMMENT '差异原因',
    operator_id    BIGINT UNSIGNED NOT NULL COMMENT '经办人',
    status         TINYINT         NOT NULL DEFAULT 10 COMMENT '10已申报 20对账通过 30对账差异',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_medins_no (settle_no),
    UNIQUE KEY uk_medins_bill (bill_id),
    KEY idx_medins_status (status, apply_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '医保结算';
