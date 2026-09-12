-- =====================================================================
-- HIS 一期 V1 全量建表（《02 数据库设计》V1.0）
-- MySQL 8.0 / InnoDB / utf8mb4
-- 公共字段规范：所有业务表含 id/created_by/created_at/updated_at/status/version；
-- sys_operation_log、sys_login_log 为 append-only 审计表，无 status/version。
-- =====================================================================

-- ----------------------------
-- 基础资料模块 bas_
-- ----------------------------
CREATE TABLE bas_organization (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    org_code    VARCHAR(32)     NOT NULL COMMENT '机构编码',
    org_name    VARCHAR(64)     NOT NULL COMMENT '机构名称',
    address     VARCHAR(128)    NULL COMMENT '地址',
    phone       VARCHAR(20)     NULL COMMENT '联系电话',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_code (org_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '组织机构';

CREATE TABLE bas_department (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    org_id      BIGINT UNSIGNED NOT NULL COMMENT '所属机构',
    dept_code   VARCHAR(32)     NOT NULL COMMENT '科室编码',
    dept_name   VARCHAR(64)     NOT NULL COMMENT '科室名称',
    dept_type   TINYINT         NOT NULL COMMENT '1临床科室 2医技科室 3药房 4收费挂号',
    location    VARCHAR(64)     NULL COMMENT '位置(楼层/诊区)',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_code (dept_code),
    KEY idx_dept_type (dept_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '科室';

CREATE TABLE bas_doctor (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '关联登录账号 sys_user.id',
    dept_id      BIGINT UNSIGNED NOT NULL COMMENT '所属临床科室',
    doctor_code  VARCHAR(32)     NOT NULL COMMENT '工号',
    doctor_name  VARCHAR(32)     NOT NULL COMMENT '姓名',
    title        VARCHAR(32)     NOT NULL COMMENT '职称',
    is_expert    TINYINT         NOT NULL DEFAULT 0 COMMENT '1专家号 0普通号',
    normal_fee   DECIMAL(10, 2)  NOT NULL COMMENT '普通号挂号费',
    expert_fee   DECIMAL(10, 2)  NOT NULL COMMENT '专家号挂号费',
    daily_quota  INT             NOT NULL DEFAULT 40 COMMENT '每日上午/下午各限挂数',
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctor_code (doctor_code),
    UNIQUE KEY uk_doctor_user (user_id),
    KEY idx_doctor_dept (dept_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '医生';

CREATE TABLE bas_drug (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    drug_code         VARCHAR(32)     NOT NULL COMMENT '药品编码',
    drug_name         VARCHAR(64)     NOT NULL COMMENT '药品名称',
    generic_name      VARCHAR(64)     NULL COMMENT '通用名',
    spec              VARCHAR(64)     NOT NULL COMMENT '规格',
    dosage_form       VARCHAR(32)     NULL COMMENT '剂型',
    category          TINYINT         NOT NULL COMMENT '1西药 2中成药 3中药饮片',
    manufacturer      VARCHAR(64)     NULL COMMENT '生产厂家',
    unit              VARCHAR(16)     NOT NULL COMMENT '最小发药单位',
    retail_price      DECIMAL(10, 2)  NOT NULL COMMENT '零售价',
    stock_warning_qty DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '库存预警下限',
    is_antibiotic     TINYINT         NOT NULL DEFAULT 0 COMMENT '1抗菌药物',
    status            TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version           INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_code (drug_code),
    KEY idx_drug_name (drug_name),
    KEY idx_drug_category (category, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '药品';

CREATE TABLE bas_charge_item (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_code  VARCHAR(32)     NOT NULL COMMENT '项目编码',
    item_name  VARCHAR(64)     NOT NULL COMMENT '项目名称',
    category   TINYINT         NOT NULL COMMENT '1挂号费 2诊查费 3检查费 4检验费 5治疗费 6材料费 7药品费',
    price      DECIMAL(10, 2)  NOT NULL COMMENT '单价',
    unit       VARCHAR(16)     NOT NULL DEFAULT '次' COMMENT '计价单位',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_code (item_code),
    KEY idx_item_category (category, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收费项目';

-- ----------------------------
-- 患者中心模块 pat_
-- ----------------------------
CREATE TABLE pat_patient (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    patient_no      VARCHAR(32)     NOT NULL COMMENT '建档号(前缀P)',
    name            VARCHAR(32)     NOT NULL COMMENT '姓名',
    gender          TINYINT         NOT NULL COMMENT '1男 2女',
    birth_date      DATE            NULL COMMENT '出生日期',
    id_card_no      VARCHAR(256)    NOT NULL COMMENT '身份证号 AES-256-GCM 密文',
    id_card_hash    CHAR(64)        NOT NULL COMMENT '身份证号 SHA-256 摘要(唯一检索)',
    phone           VARCHAR(20)     NOT NULL COMMENT '联系电话(展示层脱敏)',
    address         VARCHAR(128)    NULL COMMENT '联系地址',
    allergy_history TEXT            NULL COMMENT '过敏史',
    past_history    TEXT            NULL COMMENT '既往史',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_no (patient_no),
    UNIQUE KEY uk_id_card_hash (id_card_hash),
    KEY idx_patient_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '患者档案';

CREATE TABLE pat_medical_card (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    patient_id BIGINT UNSIGNED NOT NULL COMMENT '所属患者',
    card_no    VARCHAR(32)     NOT NULL COMMENT '卡号',
    card_type  TINYINT         NOT NULL DEFAULT 1 COMMENT '1实体卡 2电子卡',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 2挂失 3注销',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_no (card_no),
    KEY idx_card_patient (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '就诊卡';

-- ----------------------------
-- 挂号就诊模块 reg_
-- ----------------------------
CREATE TABLE reg_registration (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reg_no           VARCHAR(32)     NOT NULL COMMENT '挂号单号(前缀GH)',
    patient_id       BIGINT UNSIGNED NOT NULL COMMENT '患者',
    card_no          VARCHAR(32)     NULL COMMENT '就诊卡号(快照)',
    dept_id          BIGINT UNSIGNED NOT NULL COMMENT '科室',
    doctor_id        BIGINT UNSIGNED NOT NULL COMMENT '医生',
    reg_date         DATE            NOT NULL COMMENT '就诊日期',
    period           TINYINT         NOT NULL COMMENT '1上午 2下午',
    reg_type         TINYINT         NOT NULL COMMENT '1普通 2专家',
    reg_fee          DECIMAL(10, 2)  NOT NULL COMMENT '挂号费快照',
    consultation_fee DECIMAL(10, 2)  NOT NULL COMMENT '诊查费快照',
    queue_no         INT             NOT NULL COMMENT '当日该医生时段排队号',
    charge_status    TINYINT         NOT NULL DEFAULT 0 COMMENT '挂号费+诊查费 0未收费 1已收费',
    status           TINYINT         NOT NULL DEFAULT 10 COMMENT '10已挂号 20已退号 30已就诊 40已过号',
    created_by       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version          INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reg_no (reg_no),
    KEY idx_reg_patient (patient_id, reg_date),
    KEY idx_reg_queue (doctor_id, reg_date, period, status, queue_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '挂号单';

-- ----------------------------
-- 医生工作站模块 cli_
-- ----------------------------
CREATE TABLE cli_visit (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    visit_no        VARCHAR(32)     NOT NULL COMMENT '就诊号(前缀JZ)',
    registration_id BIGINT UNSIGNED NOT NULL COMMENT '来源挂号单(1:1)',
    patient_id      BIGINT UNSIGNED NOT NULL COMMENT '患者',
    doctor_id       BIGINT UNSIGNED NOT NULL COMMENT '接诊医生',
    dept_id         BIGINT UNSIGNED NOT NULL COMMENT '科室',
    visit_date      DATE            NOT NULL COMMENT '就诊日期',
    chief_complaint VARCHAR(512)    NULL COMMENT '主诉',
    present_illness TEXT            NULL COMMENT '现病史',
    physical_exam   VARCHAR(512)    NULL COMMENT '体格检查',
    advice          VARCHAR(512)    NULL COMMENT '处理意见',
    start_time      DATETIME        NULL COMMENT '接诊时间',
    end_time        DATETIME        NULL COMMENT '完成时间',
    status          TINYINT         NOT NULL DEFAULT 20 COMMENT '20接诊中 30已完成 40已取消(预留)',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_visit_no (visit_no),
    UNIQUE KEY uk_visit_reg (registration_id),
    KEY idx_visit_doctor (doctor_id, visit_date, status),
    KEY idx_visit_patient (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '就诊记录';

CREATE TABLE cli_diagnosis (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    visit_id       BIGINT UNSIGNED NOT NULL COMMENT '所属就诊',
    diagnosis_code VARCHAR(16)     NULL COMMENT 'ICD-10 编码(一期可空)',
    diagnosis_name VARCHAR(64)     NOT NULL COMMENT '诊断名称',
    diagnosis_type TINYINT         NOT NULL DEFAULT 2 COMMENT '1主诊断 2次诊断',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0已删除',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_diag_visit (visit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '诊断';

CREATE TABLE cli_medical_order (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    visit_id    BIGINT UNSIGNED NOT NULL COMMENT '所属就诊',
    order_type  TINYINT         NOT NULL COMMENT '1用药指导 2治疗 3复诊建议 9其他',
    content     VARCHAR(512)    NOT NULL COMMENT '医嘱内容',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0已停止',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_visit (visit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '医嘱(文字医嘱)';

CREATE TABLE cli_prescription (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rx_no          VARCHAR(32)     NOT NULL COMMENT '处方号(前缀CF)',
    visit_id       BIGINT UNSIGNED NOT NULL COMMENT '所属就诊',
    patient_id     BIGINT UNSIGNED NOT NULL COMMENT '患者',
    doctor_id      BIGINT UNSIGNED NOT NULL COMMENT '开方医生',
    dept_id        BIGINT UNSIGNED NOT NULL COMMENT '科室',
    rx_type        TINYINT         NOT NULL DEFAULT 1 COMMENT '1普通处方',
    total_amount   DECIMAL(10, 2)  NOT NULL COMMENT '处方总金额(后端重算)',
    charge_status  TINYINT         NOT NULL DEFAULT 0 COMMENT '0未收费 1已收费',
    review_by      BIGINT UNSIGNED NULL COMMENT '审核药师 user_id',
    review_at      DATETIME        NULL COMMENT '审核时间',
    review_comment VARCHAR(256)    NULL COMMENT '审核意见/驳回原因',
    void_reason    VARCHAR(256)    NULL COMMENT '作废原因(退费/退药联动)',
    status         TINYINT         NOT NULL DEFAULT 10 COMMENT '10待审核 20审核通过 30已发药 40审核驳回 50已作废',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rx_no (rx_no),
    KEY idx_rx_visit (visit_id),
    KEY idx_rx_review (status, charge_status),
    KEY idx_rx_patient (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '处方';

CREATE TABLE cli_prescription_item (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT UNSIGNED NOT NULL COMMENT '所属处方',
    drug_id         BIGINT UNSIGNED NOT NULL COMMENT '药品',
    drug_name       VARCHAR(64)     NOT NULL COMMENT '药品名快照',
    spec            VARCHAR(64)     NOT NULL COMMENT '规格快照',
    dosage          VARCHAR(32)     NOT NULL COMMENT '单次剂量 如0.25g',
    frequency       VARCHAR(16)     NOT NULL COMMENT '频次 qd/bid/tid/qid/prn',
    usage_route     VARCHAR(16)     NOT NULL COMMENT '用法 口服/静滴/肌注/外用',
    days            INT             NOT NULL COMMENT '用药天数',
    quantity        DECIMAL(12, 2)  NOT NULL COMMENT '数量',
    unit            VARCHAR(16)     NOT NULL COMMENT '单位快照',
    unit_price      DECIMAL(10, 2)  NOT NULL COMMENT '单价快照',
    amount          DECIMAL(10, 2)  NOT NULL COMMENT '金额=quantity*unit_price',
    usage_note      VARCHAR(128)    NULL COMMENT '用药嘱托',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0已作废',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_rxi_prescription (prescription_id),
    KEY idx_rxi_drug (drug_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '处方明细';

CREATE TABLE cli_exam_application (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    apply_no       VARCHAR(32)     NOT NULL COMMENT '申请单号(前缀SQ)',
    visit_id       BIGINT UNSIGNED NOT NULL COMMENT '所属就诊',
    patient_id     BIGINT UNSIGNED NOT NULL COMMENT '患者',
    doctor_id      BIGINT UNSIGNED NOT NULL COMMENT '申请医生',
    charge_item_id BIGINT UNSIGNED NOT NULL COMMENT '收费项目字典(检查/检验)',
    apply_type     TINYINT         NOT NULL COMMENT '1检查 2检验',
    requirement    VARCHAR(256)    NULL COMMENT '临床要求/备注',
    price          DECIMAL(10, 2)  NOT NULL COMMENT '单价快照',
    charge_status  TINYINT         NOT NULL DEFAULT 0 COMMENT '0未收费 1已收费',
    status         TINYINT         NOT NULL DEFAULT 10 COMMENT '10已申请 20已收费 50已作废',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_apply_no (apply_no),
    KEY idx_apply_visit (visit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '检查/检验申请';

-- ----------------------------
-- 收费结算模块 bil_
-- ----------------------------
CREATE TABLE bil_charge_bill (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bill_no         VARCHAR(32)     NOT NULL COMMENT '收费单号(前缀SF)',
    visit_id        BIGINT UNSIGNED NOT NULL COMMENT '所属就诊(1:1)',
    patient_id      BIGINT UNSIGNED NOT NULL COMMENT '患者',
    total_amount    DECIMAL(10, 2)  NOT NULL COMMENT '费用合计',
    discount_amount DECIMAL(10, 2)  NOT NULL DEFAULT 0 COMMENT '优惠(一期恒0)',
    payable_amount  DECIMAL(10, 2)  NOT NULL COMMENT '应收=total-discount',
    paid_amount     DECIMAL(10, 2)  NOT NULL DEFAULT 0 COMMENT '已收金额',
    refund_amount   DECIMAL(10, 2)  NOT NULL DEFAULT 0 COMMENT '累计退费',
    pay_method      TINYINT         NOT NULL COMMENT '1现金 2银行卡 3微信 4支付宝(一期模拟)',
    pay_time        DATETIME        NOT NULL COMMENT '支付完成时间',
    cashier_id      BIGINT UNSIGNED NOT NULL COMMENT '收费员 user_id',
    status          TINYINT         NOT NULL DEFAULT 10 COMMENT '10已支付 20部分退费 30全额退费',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_no (bill_no),
    UNIQUE KEY uk_bill_visit (visit_id),
    KEY idx_bill_patient (patient_id),
    KEY idx_bill_paytime (pay_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收费单';

CREATE TABLE bil_charge_detail (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bill_id          BIGINT UNSIGNED NOT NULL COMMENT '所属收费单',
    visit_id         BIGINT UNSIGNED NOT NULL COMMENT '就诊(冗余,报表用)',
    patient_id       BIGINT UNSIGNED NOT NULL COMMENT '患者(冗余)',
    fee_type         TINYINT         NOT NULL COMMENT '1挂号费 2诊查费 3检查费 4检验费 5治疗费 6材料费 7药品费',
    source_type      TINYINT         NOT NULL COMMENT '1挂号单 2处方明细 3检查申请',
    source_detail_id BIGINT UNSIGNED NOT NULL COMMENT '来源行ID',
    item_name        VARCHAR(64)     NOT NULL COMMENT '项目名快照',
    quantity         DECIMAL(12, 2)  NOT NULL COMMENT '数量',
    unit_price       DECIMAL(10, 2)  NOT NULL COMMENT '单价快照',
    amount           DECIMAL(10, 2)  NOT NULL COMMENT '金额',
    refund_status    TINYINT         NOT NULL DEFAULT 0 COMMENT '0未退 1部分退 2全退',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0已作废',
    created_by       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version          INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cd_bill (bill_id),
    KEY idx_cd_source (source_type, source_detail_id),
    KEY idx_cd_visit (visit_id, fee_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '费用明细';

CREATE TABLE bil_payment_record (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bill_id        BIGINT UNSIGNED NOT NULL COMMENT '所属收费单',
    pay_no         VARCHAR(32)     NOT NULL COMMENT '支付流水号',
    pay_method     TINYINT         NOT NULL COMMENT '同收费单 pay_method',
    amount         DECIMAL(10, 2)  NOT NULL COMMENT '支付金额',
    transaction_id VARCHAR(64)     NULL COMMENT '第三方流水号(一期模拟)',
    pay_status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1成功 2失败(留痕)',
    pay_time       DATETIME        NOT NULL COMMENT '支付时间',
    cashier_id     BIGINT UNSIGNED NOT NULL COMMENT '收费员',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_no (pay_no),
    KEY idx_pay_bill (bill_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付记录';

CREATE TABLE bil_refund_bill (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no     VARCHAR(32)     NOT NULL COMMENT '退费单号(前缀TF)',
    bill_id       BIGINT UNSIGNED NOT NULL COMMENT '原收费单',
    visit_id      BIGINT UNSIGNED NOT NULL COMMENT '就诊(冗余)',
    patient_id    BIGINT UNSIGNED NOT NULL COMMENT '患者(冗余)',
    refund_amount DECIMAL(10, 2)  NOT NULL COMMENT '退费金额',
    reason        VARCHAR(256)    NOT NULL COMMENT '退费原因',
    refund_method TINYINT         NOT NULL COMMENT '1现金 2原路退回(一期模拟)',
    refund_time   DATETIME        NOT NULL COMMENT '退费时间',
    operator_id   BIGINT UNSIGNED NOT NULL COMMENT '操作收费员',
    status        TINYINT         NOT NULL DEFAULT 10 COMMENT '10已退费 20已驳回',
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_refund_bill (bill_id),
    KEY idx_refund_time (refund_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退费单';

CREATE TABLE bil_refund_detail (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_bill_id   BIGINT UNSIGNED NOT NULL COMMENT '所属退费单',
    charge_detail_id BIGINT UNSIGNED NOT NULL COMMENT '原费用明细',
    refund_quantity  DECIMAL(12, 2)  NOT NULL COMMENT '退数量',
    refund_amount    DECIMAL(10, 2)  NOT NULL COMMENT '退金额',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效',
    created_by       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version          INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_rd_refund (refund_bill_id),
    KEY idx_rd_charge_detail (charge_detail_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退费明细';

CREATE TABLE bil_daily_settlement (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    settlement_no        VARCHAR(32)     NOT NULL COMMENT '日结单号(前缀RJ)',
    settle_date          DATE            NOT NULL COMMENT '日结日期',
    cashier_id           BIGINT UNSIGNED NOT NULL COMMENT '收费员(个人日结)',
    bill_count           INT             NOT NULL COMMENT '收费单数',
    refund_count         INT             NOT NULL COMMENT '退费单数',
    total_charge_amount  DECIMAL(12, 2)  NOT NULL COMMENT '收费合计',
    total_refund_amount  DECIMAL(12, 2)  NOT NULL COMMENT '退费合计',
    net_amount           DECIMAL(12, 2)  NOT NULL COMMENT '净收入',
    status               TINYINT         NOT NULL DEFAULT 1 COMMENT '1已日结',
    created_by           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version              INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settle_no (settlement_no),
    UNIQUE KEY uk_settle_date_cashier (settle_date, cashier_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '日结';

-- ----------------------------
-- 药房库存模块 phr_ / inv_
-- ----------------------------
CREATE TABLE phr_review_record (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT UNSIGNED NOT NULL COMMENT '处方',
    reviewer_id     BIGINT UNSIGNED NOT NULL COMMENT '审核药师',
    review_action   TINYINT         NOT NULL COMMENT '1通过 2驳回',
    comment         VARCHAR(256)    NULL COMMENT '审核意见',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_review_rx (prescription_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '处方审核记录';

CREATE TABLE phr_dispense_order (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dispense_no     VARCHAR(32)     NOT NULL COMMENT '发药单号(前缀FY)',
    prescription_id BIGINT UNSIGNED NOT NULL COMMENT '处方(唯一=一处方至多一次发药)',
    patient_id      BIGINT UNSIGNED NOT NULL COMMENT '患者(冗余)',
    dispenser_id    BIGINT UNSIGNED NOT NULL COMMENT '发药药师',
    total_quantity  DECIMAL(12, 2)  NOT NULL COMMENT '发药总数量',
    dispense_time   DATETIME        NOT NULL COMMENT '发药时间',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1已发药',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispense_no (dispense_no),
    UNIQUE KEY uk_dispense_rx (prescription_id),
    KEY idx_dispense_time (dispense_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发药单';

CREATE TABLE phr_dispense_detail (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dispense_order_id    BIGINT UNSIGNED NOT NULL COMMENT '所属发药单',
    prescription_item_id BIGINT UNSIGNED NOT NULL COMMENT '处方明细行',
    drug_id              BIGINT UNSIGNED NOT NULL COMMENT '药品',
    batch_id             BIGINT UNSIGNED NOT NULL COMMENT '发放库存批次(FEFO选取)',
    quantity             DECIMAL(12, 2)  NOT NULL COMMENT '本批发放数量',
    status               TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效',
    created_by           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version              INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_dd_dispense (dispense_order_id),
    KEY idx_dd_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发药明细';

CREATE TABLE phr_return_order (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    return_no         VARCHAR(32)     NOT NULL COMMENT '退药单号(前缀TY)',
    dispense_order_id BIGINT UNSIGNED NOT NULL COMMENT '原发药单',
    prescription_id   BIGINT UNSIGNED NOT NULL COMMENT '处方(冗余)',
    patient_id        BIGINT UNSIGNED NOT NULL COMMENT '患者(冗余)',
    reason            VARCHAR(256)    NOT NULL COMMENT '退药原因',
    return_time       DATETIME        NOT NULL COMMENT '退药时间',
    operator_id       BIGINT UNSIGNED NOT NULL COMMENT '操作药师',
    status            TINYINT         NOT NULL DEFAULT 10 COMMENT '10已退药 20已驳回',
    created_by        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version           INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_return_no (return_no),
    KEY idx_return_dispense (dispense_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退药单';

CREATE TABLE phr_return_detail (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    return_order_id       BIGINT UNSIGNED NOT NULL COMMENT '所属退药单',
    prescription_item_id  BIGINT UNSIGNED NOT NULL COMMENT '原处方明细行',
    drug_id               BIGINT UNSIGNED NOT NULL COMMENT '药品',
    batch_id              BIGINT UNSIGNED NOT NULL COMMENT '退回批次',
    quantity              DECIMAL(12, 2)  NOT NULL COMMENT '退回数量',
    status                TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效',
    created_by            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version               INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_rtd_return (return_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退药明细';

CREATE TABLE inv_inbound_order (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    inbound_no  VARCHAR(32)     NOT NULL COMMENT '入库单号(前缀RK)',
    drug_id     BIGINT UNSIGNED NOT NULL COMMENT '药品',
    batch_no    VARCHAR(32)     NOT NULL COMMENT '生产批号',
    expiry_date DATE            NOT NULL COMMENT '失效日期',
    quantity    DECIMAL(12, 2)  NOT NULL COMMENT '入库数量(>0)',
    unit_price  DECIMAL(10, 2)  NULL COMMENT '采购单价',
    supplier    VARCHAR(64)     NULL COMMENT '供应商',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '入库操作人',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1已入库',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbound_no (inbound_no),
    KEY idx_inbound_drug (drug_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '入库单(一品一行)';

CREATE TABLE inv_inventory_batch (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    drug_id          BIGINT UNSIGNED NOT NULL COMMENT '药品',
    batch_no         VARCHAR(32)     NOT NULL COMMENT '生产批号',
    expiry_date      DATE            NOT NULL COMMENT '失效日期',
    quantity         DECIMAL(12, 2)  NOT NULL COMMENT '当前可用数量(条件更新>=0)',
    initial_quantity DECIMAL(12, 2)  NOT NULL COMMENT '初始数量(核对用)',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1可用 2已冻结 3已过期 4已用尽',
    created_by       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version          INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch (drug_id, batch_no),
    KEY idx_batch_pick (drug_id, status, expiry_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存批次';

CREATE TABLE inv_stock_movement (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    drug_id       BIGINT UNSIGNED NOT NULL COMMENT '药品',
    batch_id      BIGINT UNSIGNED NOT NULL COMMENT '批次',
    movement_type TINYINT         NOT NULL COMMENT '1入库 2发药出库 3退药入库 4盘点调整 5过期核销',
    quantity      DECIMAL(12, 2)  NOT NULL COMMENT '变动数量(带符号)',
    before_qty    DECIMAL(12, 2)  NOT NULL COMMENT '变动前',
    after_qty     DECIMAL(12, 2)  NOT NULL COMMENT '变动后',
    ref_type      TINYINT         NOT NULL COMMENT '1入库单 2发药单 3退药单 4盘点单',
    ref_no        VARCHAR(32)     NOT NULL COMMENT '关联单号',
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '操作人',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mv_drug (drug_id, created_at),
    KEY idx_mv_ref (ref_type, ref_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存流水(append-only)';

-- ----------------------------
-- 权限审计模块 sys_
-- ----------------------------
CREATE TABLE sys_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)     NOT NULL COMMENT '登录名',
    password_hash VARCHAR(72)     NOT NULL COMMENT 'BCrypt 散列',
    real_name     VARCHAR(32)     NOT NULL COMMENT '姓名',
    phone         VARCHAR(20)     NULL COMMENT '联系电话',
    last_login_at DATETIME        NULL COMMENT '最后登录时间',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

CREATE TABLE sys_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_code   VARCHAR(32)     NOT NULL COMMENT 'ADMIN/DOCTOR/CASHIER/PHARMACIST/AUDITOR',
    role_name   VARCHAR(32)     NOT NULL COMMENT '角色名称',
    description VARCHAR(128)    NULL COMMENT '说明',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

CREATE TABLE sys_menu (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父级',
    menu_name       VARCHAR(32)     NOT NULL COMMENT '名称',
    menu_type       TINYINT         NOT NULL COMMENT '1目录 2菜单 3按钮/接口权限点',
    permission_code VARCHAR(64)     NULL COMMENT '权限码(权限点必填,唯一)',
    path            VARCHAR(128)    NULL COMMENT '前端路由(菜单类)',
    component       VARCHAR(128)    NULL COMMENT '前端组件路径(菜单类)',
    sort_no         INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (permission_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单与权限点';

CREATE TABLE sys_user_role (
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色关联';

CREATE TABLE sys_role_menu (
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色',
    menu_id BIGINT UNSIGNED NOT NULL COMMENT '菜单/权限点',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-菜单关联';

CREATE TABLE sys_operation_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trace_id    VARCHAR(32)     NOT NULL COMMENT '链路追踪ID',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '操作人',
    username    VARCHAR(32)     NOT NULL COMMENT '登录名(冗余)',
    module      VARCHAR(32)     NOT NULL COMMENT '模块',
    action      VARCHAR(64)     NOT NULL COMMENT '动作',
    biz_type    VARCHAR(32)     NULL COMMENT '业务类型',
    biz_id      VARCHAR(32)     NULL COMMENT '业务单号',
    method      VARCHAR(128)    NULL COMMENT '请求方法',
    params_json TEXT            NULL COMMENT '请求参数(脱敏)',
    result_code VARCHAR(16)     NOT NULL COMMENT '结果码',
    ip          VARCHAR(64)     NULL COMMENT '客户端IP',
    user_agent  VARCHAR(256)    NULL COMMENT 'UA',
    cost_ms     INT             NULL COMMENT '耗时ms',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_oplog_user (user_id, created_at),
    KEY idx_oplog_biz (biz_type, biz_id),
    KEY idx_oplog_module (module, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志(append-only)';

CREATE TABLE sys_login_log (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username   VARCHAR(32)     NOT NULL COMMENT '登录名',
    ip         VARCHAR(64)     NULL COMMENT '客户端IP',
    user_agent VARCHAR(256)    NULL COMMENT 'UA',
    success    TINYINT         NOT NULL COMMENT '1成功 0失败',
    message    VARCHAR(128)    NULL COMMENT '结果信息',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_loginlog_user (username, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录日志(append-only)';
