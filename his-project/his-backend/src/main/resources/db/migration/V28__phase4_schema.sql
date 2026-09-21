-- =====================================================================
-- HIS V28 四期新表与加列（《22-四期数据库与接口设计》）
-- =====================================================================

-- ----------------------------
-- 输血 bb_
-- ----------------------------
CREATE TABLE bb_blood_bag (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bag_no       VARCHAR(32)     NOT NULL COMMENT '血袋号(前缀XDJ)',
    blood_type   TINYINT         NOT NULL COMMENT '1 A 2 B 3 AB 4 O',
    rh           TINYINT         NOT NULL DEFAULT 1 COMMENT '1 阳性 2 阴性',
    component    TINYINT         NOT NULL COMMENT '1 红细胞 2 血浆 3 血小板 4 冷沉淀',
    volume_ml    INT             NOT NULL DEFAULT 200 COMMENT '容量ml',
    blood_station VARCHAR(64)    NULL COMMENT '血站来源',
    collect_date DATE            NULL COMMENT '采集日期',
    expire_date  DATE            NOT NULL COMMENT '失效日期',
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1在库 2已发用 3过期报废 4退回',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bag_no (bag_no),
    KEY idx_bag_avail (blood_type, component, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '血袋(血库库存)';

CREATE TABLE bb_request (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    req_no      VARCHAR(32)     NOT NULL COMMENT '用血申请号(前缀XY)',
    admission_id BIGINT UNSIGNED NOT NULL COMMENT '住院',
    patient_id  BIGINT UNSIGNED NOT NULL COMMENT '患者',
    doctor_id   BIGINT UNSIGNED NOT NULL COMMENT '申请医生',
    blood_type  TINYINT         NOT NULL COMMENT '1 A 2 B 3 AB 4 O',
    rh          TINYINT         NOT NULL DEFAULT 1 COMMENT '1 阳性 2 阴性',
    component   TINYINT         NOT NULL COMMENT '1 红细胞 2 血浆 3 血小板 4 冷沉淀',
    volume_ml   INT             NOT NULL COMMENT '申请量ml',
    use_purpose VARCHAR(256)    NULL COMMENT '用血目的',
    reviewer_id BIGINT UNSIGNED NULL COMMENT '审核人(血库)',
    review_note VARCHAR(256)    NULL COMMENT '审核意见',
    status      TINYINT         NOT NULL DEFAULT 10 COMMENT '10待审核 20配血中 30配血完成 40已发血 50输血中 60已完成 70已取消 80已驳回',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bbreq_no (req_no),
    KEY idx_bbreq_adm (admission_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用血申请';

CREATE TABLE bb_cross_match (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id   BIGINT UNSIGNED NOT NULL COMMENT '用血申请',
    bag_id       BIGINT UNSIGNED NOT NULL COMMENT '血袋',
    cross_method VARCHAR(32)     NOT NULL COMMENT '配血方法(盐水介质/抗人球等)',
    cross_result TINYINT         NOT NULL COMMENT '1 相容 2 不相容',
    matcher_id   BIGINT UNSIGNED NOT NULL COMMENT '配血者',
    match_time   DATETIME        NOT NULL COMMENT '配血时间',
    note         VARCHAR(256)    NULL COMMENT '备注',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cross_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '交叉配血';

CREATE TABLE bb_issue (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id  BIGINT UNSIGNED NOT NULL COMMENT '用血申请',
    bag_id      BIGINT UNSIGNED NOT NULL COMMENT '血袋',
    issuer_id   BIGINT UNSIGNED NOT NULL COMMENT '发血者',
    receiver_id BIGINT UNSIGNED NOT NULL COMMENT '取血护士',
    issue_time  DATETIME        NOT NULL COMMENT '发血时间',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_req_bag (request_id, bag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发血';

CREATE TABLE bb_transfusion (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id   BIGINT UNSIGNED NOT NULL COMMENT '用血申请',
    bag_id       BIGINT UNSIGNED NOT NULL COMMENT '血袋',
    executor_id  BIGINT UNSIGNED NOT NULL COMMENT '执行护士',
    checker1_id  BIGINT UNSIGNED NOT NULL COMMENT '床边核对1',
    checker2_id  BIGINT UNSIGNED NOT NULL COMMENT '床边核对2(须不同人)',
    vital_before VARCHAR(512)    NULL COMMENT '输血前体征JSON',
    start_time   DATETIME        NOT NULL COMMENT '开始输注',
    end_time     DATETIME        NULL COMMENT '结束',
    outcome      TINYINT         NULL COMMENT '1 正常 2 有反应',
    note         VARCHAR(256)    NULL COMMENT '备注',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bbtrans_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '输血执行';

CREATE TABLE bb_adverse (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id  BIGINT UNSIGNED NOT NULL COMMENT '用血申请',
    type        TINYINT         NOT NULL COMMENT '1 发热 2 过敏 3 溶血 4 其他',
    severity    TINYINT         NOT NULL COMMENT '1 轻 2 中 3 重',
    handle_note VARCHAR(512)    NOT NULL COMMENT '处置记录',
    reporter_id BIGINT UNSIGNED NOT NULL COMMENT '报告人',
    report_time DATETIME        NOT NULL COMMENT '报告时间',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_adv_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '输血不良反应';

-- ----------------------------
-- 耗材批次
-- ----------------------------
CREATE TABLE mat_batch (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    material_id BIGINT UNSIGNED NOT NULL COMMENT '物资',
    batch_no    VARCHAR(32)     NOT NULL COMMENT '批次号',
    expire_date DATE            NOT NULL COMMENT '失效日期',
    quantity    INT             NOT NULL DEFAULT 0 COMMENT '批次数量',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1在库 0拨尽',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_matbatch (material_id, batch_no),
    KEY idx_matbatch_expire (material_id, expire_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物资批次明细';

-- ----------------------------
-- 加列
-- ----------------------------
ALTER TABLE cdss_rule
    ADD COLUMN allergy_keyword VARCHAR(64) NULL COMMENT 'type=4 过敏原关键字(患者过敏史包含即匹配)' AFTER ref_b_id,
    ADD COLUMN age_min INT NULL COMMENT 'type=3 生效年龄下限(岁)' AFTER allergy_keyword,
    ADD COLUMN age_max INT NULL COMMENT 'type=3 生效年龄上限(岁)' AFTER age_min;

ALTER TABLE lis_report
    ADD COLUMN mutual_flag TINYINT NOT NULL DEFAULT 0 COMMENT '1 纳入互认(HR标识)' AFTER status,
    ADD COLUMN mutual_note VARCHAR(128) NULL COMMENT '互认备注' AFTER mutual_flag;

ALTER TABLE ris_report
    ADD COLUMN mutual_flag TINYINT NOT NULL DEFAULT 0 COMMENT '1 纳入互认(HR标识)' AFTER status,
    ADD COLUMN mutual_note VARCHAR(128) NULL COMMENT '互认备注' AFTER mutual_flag;

ALTER TABLE mat_requisition
    ADD COLUMN breakdown JSON NULL COMMENT 'FEFO拨发拆分[{batchNo,quantity}]' AFTER status;

ALTER TABLE bas_charge_item
    MODIFY COLUMN category TINYINT NOT NULL
    COMMENT '1挂号费 2诊查费 3检查费 4检验费 5治疗费 6材料费 7药品费 8床位费 9手术费 10麻醉费 11血费';
