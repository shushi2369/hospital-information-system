-- =====================================================================
-- HIS V13 三期第一批新表（《13-三期数据库与接口设计》）
-- =====================================================================

-- ----------------------------
-- LIS 检验
-- ----------------------------
CREATE TABLE lis_request (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_no    VARCHAR(32)     NOT NULL COMMENT '申请单号(前缀JY)',
    order_id      BIGINT UNSIGNED NOT NULL COMMENT '来源检验医嘱',
    admission_id  BIGINT UNSIGNED NOT NULL COMMENT '住院',
    patient_id    BIGINT UNSIGNED NOT NULL COMMENT '患者',
    doctor_id     BIGINT UNSIGNED NOT NULL COMMENT '开单医生',
    specimen_type VARCHAR(16)     NOT NULL DEFAULT '静脉血' COMMENT '标本类型',
    status        TINYINT         NOT NULL DEFAULT 10 COMMENT '10待采集 20已采集 30检验中 40已报告 50作废',
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_lisreq_adm (admission_id, status),
    KEY idx_lisreq_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '检验申请单';

CREATE TABLE lis_specimen (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    specimen_no  VARCHAR(32)     NOT NULL COMMENT '标本条码(前缀BB)',
    request_id   BIGINT UNSIGNED NOT NULL COMMENT '申请单',
    collected_at DATETIME        NULL COMMENT '采集时间',
    collector_id BIGINT UNSIGNED NULL COMMENT '采集护士',
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1已采集 2已接收 3作废',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_specimen_no (specimen_no),
    KEY idx_spec_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标本(条码)';

CREATE TABLE lis_result (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id      BIGINT UNSIGNED NOT NULL COMMENT '申请单',
    item_name       VARCHAR(64)     NOT NULL COMMENT '检验项目名',
    result_value    VARCHAR(64)     NOT NULL COMMENT '结果值',
    unit            VARCHAR(16)     NULL COMMENT '单位',
    reference_range VARCHAR(32)     NULL COMMENT '参考范围',
    abnormal_flag   TINYINT         NOT NULL DEFAULT 0 COMMENT '0正常 1偏高H 2偏低L',
    critical_flag   TINYINT         NOT NULL DEFAULT 0 COMMENT '1危急',
    instrument      VARCHAR(32)     NULL COMMENT '仪器(Mock)',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_result_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '检验结果';

CREATE TABLE lis_report (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    report_no      VARCHAR(32)     NOT NULL COMMENT '报告号(前缀BG)',
    request_id     BIGINT UNSIGNED NOT NULL COMMENT '申请单',
    result_summary VARCHAR(256)    NULL COMMENT '异常项摘要',
    reporter_id    BIGINT UNSIGNED NOT NULL COMMENT '发布技师',
    report_time    DATETIME        NOT NULL COMMENT '发布时间',
    audited_by     BIGINT UNSIGNED NULL COMMENT '双签(预留)',
    status         TINYINT         NOT NULL DEFAULT 20 COMMENT '10编制中 20已发布',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_no (report_no),
    UNIQUE KEY uk_report_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '检验报告';

-- ----------------------------
-- 危急值
-- ----------------------------
CREATE TABLE alert_critical (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    alert_no        VARCHAR(32)     NOT NULL COMMENT '危急值号(前缀WJ)',
    source          TINYINT         NOT NULL DEFAULT 1 COMMENT '1 LIS(预留2 PACS)',
    request_id      BIGINT UNSIGNED NOT NULL COMMENT '检验申请',
    result_id       BIGINT UNSIGNED NOT NULL COMMENT '危急结果行',
    patient_id      BIGINT UNSIGNED NOT NULL COMMENT '患者',
    admission_id    BIGINT UNSIGNED NOT NULL COMMENT '住院',
    item_name       VARCHAR(64)     NOT NULL COMMENT '项目',
    critical_value  VARCHAR(64)     NOT NULL COMMENT '危急值',
    notified_nurse  BIGINT UNSIGNED NULL COMMENT '通知护士',
    notified_at     DATETIME        NULL COMMENT '通知时间',
    confirmed_doctor BIGINT UNSIGNED NULL COMMENT '确认医生',
    confirmed_at    DATETIME        NULL COMMENT '确认时间',
    handle_note     VARCHAR(256)    NULL COMMENT '处置意见',
    status          TINYINT         NOT NULL DEFAULT 10 COMMENT '10待处理 20已通知 30已确认 40已闭环',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_no (alert_no),
    KEY idx_alert_status (status, created_at),
    KEY idx_alert_adm (admission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '危急值(闭环)';

-- ----------------------------
-- 药库
-- ----------------------------
CREATE TABLE bas_supplier (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    supplier_code VARCHAR(32)    NOT NULL COMMENT '供应商编码',
    supplier_name VARCHAR(64)    NOT NULL COMMENT '供应商名称',
    contact      VARCHAR(32)     NULL COMMENT '联系人',
    phone        VARCHAR(20)     NULL,
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (supplier_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商字典';

CREATE TABLE whse_purchase_order (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    po_no        VARCHAR(32)     NOT NULL COMMENT '采购单号(前缀CG)',
    supplier_id  BIGINT UNSIGNED NOT NULL COMMENT '供应商',
    drug_id      BIGINT UNSIGNED NOT NULL COMMENT '药品',
    quantity     DECIMAL(12, 2)  NOT NULL COMMENT '采购数量',
    unit_price   DECIMAL(10, 2)  NOT NULL COMMENT '采购单价',
    expected_date DATE           NULL COMMENT '预计到货',
    status       TINYINT         NOT NULL DEFAULT 10 COMMENT '10待审批 20已下单 30已到货入库 40已取消',
    inbound_no   VARCHAR(32)     NULL COMMENT '到货入库单号',
    approver_id  BIGINT UNSIGNED NULL COMMENT '审批人',
    approved_at  DATETIME        NULL COMMENT '审批时间',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_no (po_no),
    KEY idx_po_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购订单';

CREATE TABLE lis_critical_threshold (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_name  VARCHAR(64)     NOT NULL COMMENT '检验项目名',
    low_value  DECIMAL(12, 2)  NULL COMMENT '危急下限',
    high_value DECIMAL(12, 2)  NULL COMMENT '危急上限',
    status     TINYINT         NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_thresh_item (item_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '危急值阈值';
