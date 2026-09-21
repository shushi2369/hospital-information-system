-- =====================================================================
-- HIS V22 三期第三批新表（《19-三期第三批数据库与接口设计》）
-- =====================================================================

-- ----------------------------
-- HR 人事
-- ----------------------------
CREATE TABLE hr_staff (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    staff_no   VARCHAR(32)     NOT NULL COMMENT '员工号(前缀YG)',
    user_id    BIGINT UNSIGNED NULL COMMENT '关联系统账号(可空弱关联)',
    name       VARCHAR(64)     NOT NULL COMMENT '姓名',
    dept_id    BIGINT UNSIGNED NOT NULL COMMENT '科室',
    title      VARCHAR(32)     NOT NULL DEFAULT '医师' COMMENT '职称',
    license_no VARCHAR(64)     NULL COMMENT '执业证号',
    phone      VARCHAR(16)     NULL COMMENT '联系电话',
    entry_date DATE            NOT NULL COMMENT '入职日期',
    exit_date  DATE            NULL COMMENT '离职日期',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1在职 0离职',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_no (staff_no),
    KEY idx_staff_dept (dept_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '员工档案';

CREATE TABLE hr_title_change (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    staff_id       BIGINT UNSIGNED NOT NULL COMMENT '员工',
    old_title      VARCHAR(32)     NOT NULL COMMENT '原职称',
    new_title      VARCHAR(32)     NOT NULL COMMENT '新职称',
    effective_date DATE            NOT NULL COMMENT '生效日期',
    approver_id    BIGINT UNSIGNED NOT NULL COMMENT '审批人',
    note           VARCHAR(256)    NULL COMMENT '备注',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_titlechg_staff (staff_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '职称变更记录';

-- ----------------------------
-- 物资耗材
-- ----------------------------
CREATE TABLE mat_material (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    material_code VARCHAR(32)     NOT NULL COMMENT '物资编码',
    name          VARCHAR(64)     NOT NULL COMMENT '物资名称',
    category      TINYINT         NOT NULL COMMENT '1卫生材料 2低值易耗 3办公用品 4试剂',
    unit          VARCHAR(16)     NOT NULL DEFAULT '件' COMMENT '单位',
    price         DECIMAL(10, 2)  NOT NULL COMMENT '参考单价',
    safe_stock    INT             NOT NULL DEFAULT 0 COMMENT '安全库存',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_code (material_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物资字典';

CREATE TABLE mat_stock (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    material_id BIGINT UNSIGNED NOT NULL COMMENT '物资',
    quantity    INT             NOT NULL DEFAULT 0 COMMENT '库存数量',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_matstock_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物资库存';

CREATE TABLE mat_purchase (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    po_no         VARCHAR(32)     NOT NULL COMMENT '采购单号(前缀MC)',
    supplier_id   BIGINT UNSIGNED NOT NULL COMMENT '供应商(复用bas_supplier)',
    material_id   BIGINT UNSIGNED NOT NULL COMMENT '物资',
    quantity      INT             NOT NULL COMMENT '采购数量',
    unit_price    DECIMAL(10, 2)  NOT NULL COMMENT '单价快照',
    expected_date DATE            NULL COMMENT '预计到货',
    approver_id   BIGINT UNSIGNED NULL COMMENT '审批人',
    approved_at   DATETIME        NULL COMMENT '审批时间',
    status        TINYINT         NOT NULL DEFAULT 10 COMMENT '10待审批 20已下单 30已入库 40已取消',
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_matpo_no (po_no),
    KEY idx_matpo_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物资采购单';

CREATE TABLE mat_requisition (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    req_no       VARCHAR(32)     NOT NULL COMMENT '领用单号(前缀LY)',
    material_id  BIGINT UNSIGNED NOT NULL COMMENT '物资',
    dept_id      BIGINT UNSIGNED NOT NULL COMMENT '领用科室',
    quantity     INT             NOT NULL COMMENT '领用数量',
    applicant_id BIGINT UNSIGNED NOT NULL COMMENT '领用人',
    purpose      VARCHAR(128)    NULL COMMENT '用途',
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1已领用',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_matreq_no (req_no),
    KEY idx_matreq_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '科室领用';

-- ----------------------------
-- 体检
-- ----------------------------
CREATE TABLE pe_package (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    package_no VARCHAR(32)     NOT NULL COMMENT '套餐号(前缀TC)',
    name       VARCHAR(64)     NOT NULL COMMENT '套餐名称',
    price      DECIMAL(10, 2)  NOT NULL COMMENT '套餐价',
    items      JSON            NOT NULL COMMENT '项目[{chargeItemId,itemName,price}]',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pepkg_no (package_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '体检套餐';

CREATE TABLE pe_record (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_no  VARCHAR(32)     NOT NULL COMMENT '体检登记号(前缀TJ)',
    patient_id BIGINT UNSIGNED NOT NULL COMMENT '体检人(复用患者建档)',
    package_id BIGINT UNSIGNED NOT NULL COMMENT '套餐',
    exam_date  DATE            NOT NULL COMMENT '体检日期',
    status     TINYINT         NOT NULL DEFAULT 10 COMMENT '10已登记 20检查中 30已完成 40报告已发',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perec_no (record_no),
    KEY idx_perec_status (status, exam_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '体检登记';

CREATE TABLE pe_result (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_id      BIGINT UNSIGNED NOT NULL COMMENT '体检登记',
    charge_item_id BIGINT UNSIGNED NOT NULL COMMENT '收费项目',
    item_name      VARCHAR(64)     NOT NULL COMMENT '项目名快照',
    result_value   VARCHAR(128)    NOT NULL COMMENT '结果值',
    abnormal_flag  TINYINT         NOT NULL DEFAULT 0 COMMENT '0正常 1异常',
    examiner_id    BIGINT UNSIGNED NOT NULL COMMENT '检查者',
    note           VARCHAR(256)    NULL COMMENT '备注',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_peresult (record_id, charge_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '体检分项结果';

CREATE TABLE pe_report (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    report_no   VARCHAR(32)     NOT NULL COMMENT '体检报告号(前缀TJB)',
    record_id   BIGINT UNSIGNED NOT NULL COMMENT '体检登记',
    summary     VARCHAR(1024)   NOT NULL COMMENT '总检结论',
    doctor_id   BIGINT UNSIGNED NOT NULL COMMENT '总检医生',
    report_time DATETIME        NOT NULL COMMENT '发布时间',
    status      TINYINT         NOT NULL DEFAULT 20 COMMENT '20已发布',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pereport_no (report_no),
    UNIQUE KEY uk_pereport_record (record_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '体检报告';

-- ----------------------------
-- CDSS
-- ----------------------------
CREATE TABLE cdss_rule (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rule_code  VARCHAR(32)     NOT NULL COMMENT '规则编码',
    rule_type  TINYINT         NOT NULL COMMENT '1配伍禁忌 2重复检查 3剂量上限',
    ref_a_id   BIGINT UNSIGNED NOT NULL COMMENT '参照A(药品id/收费项目id)',
    ref_b_id   BIGINT UNSIGNED NULL COMMENT '参照B(配伍对/重复项目; type=3 时为上限值整型化)',
    level      TINYINT         NOT NULL DEFAULT 1 COMMENT '1提示(本批全部提示级)',
    message    VARCHAR(256)    NOT NULL COMMENT '提示文案',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cdssrule_code (rule_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'CDSS规则';

CREATE TABLE cdss_hit (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id   BIGINT UNSIGNED NOT NULL COMMENT '医嘱',
    doctor_id  BIGINT UNSIGNED NOT NULL COMMENT '开单医生(冗余,数据范围过滤)',
    rule_id    BIGINT UNSIGNED NOT NULL COMMENT '规则',
    message    VARCHAR(256)    NOT NULL COMMENT '提示快照',
    ignored    TINYINT         NOT NULL DEFAULT 1 COMMENT '1医生坚持开立 0提示后放弃',
    hit_time   DATETIME        NOT NULL COMMENT '命中时间',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cdsshit_order (order_id),
    KEY idx_cdsshit_doctor (doctor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'CDSS命中记录';
