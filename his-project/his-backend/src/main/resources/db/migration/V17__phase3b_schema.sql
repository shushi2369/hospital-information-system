-- =====================================================================
-- HIS V17 三期第二批新表（《16-三期第二批数据库与接口设计》）
-- =====================================================================

-- ----------------------------
-- ORIS 手术麻醉
-- ----------------------------
CREATE TABLE or_operate_room (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    room_no    VARCHAR(32)     NOT NULL COMMENT '手术间编号',
    room_name  VARCHAR(64)     NOT NULL COMMENT '手术间名称',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1可用 0维护',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_no (room_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '手术间';

CREATE TABLE or_surgery_request (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_no         VARCHAR(32)     NOT NULL COMMENT '手术申请号(前缀SS)',
    admission_id       BIGINT UNSIGNED NOT NULL COMMENT '住院',
    patient_id         BIGINT UNSIGNED NOT NULL COMMENT '患者',
    applicant_doctor_id BIGINT UNSIGNED NOT NULL COMMENT '申请医生',
    surgery_name       VARCHAR(64)     NOT NULL COMMENT '手术名称',
    surgery_code       VARCHAR(32)     NULL COMMENT '术式编码(ICD-9-CM-3)',
    diagnosis          VARCHAR(256)    NOT NULL COMMENT '术前诊断快照',
    planned_date       DATE            NOT NULL COMMENT '拟手术日期',
    anesthesia_method  TINYINT         NOT NULL COMMENT '1全麻 2椎管内 3神经阻滞 4局麻+镇静 5基础麻醉',
    surgery_item_id    BIGINT UNSIGNED NULL COMMENT '手术收费项目',
    anesthesia_item_id BIGINT UNSIGNED NULL COMMENT '麻醉收费项目',
    surgery_price      DECIMAL(10, 2)  NULL COMMENT '手术单价快照',
    anesthesia_price   DECIMAL(10, 2)  NULL COMMENT '麻醉单价快照',
    incision_time      DATETIME        NULL COMMENT '切皮时间',
    end_time           DATETIME        NULL COMMENT '手术结束时间',
    status             TINYINT         NOT NULL DEFAULT 10 COMMENT '10待审核 20已审核 30已排台 40术中 50复苏中 60已完成 70已取消',
    charge_status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0未记账 1已记账(完成关档后)',
    created_by         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version            INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_or_req_no (request_no),
    KEY idx_orreq_adm (admission_id, status),
    KEY idx_orreq_status (status, planned_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '手术申请单';

CREATE TABLE or_schedule (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    schedule_no         VARCHAR(32)     NOT NULL COMMENT '排台单号(前缀PT)',
    request_id          BIGINT UNSIGNED NOT NULL COMMENT '手术申请',
    room_id             BIGINT UNSIGNED NOT NULL COMMENT '手术间',
    surgery_date        DATE            NOT NULL COMMENT '手术日期',
    seq_no              TINYINT         NOT NULL COMMENT '台次',
    start_time          DATETIME        NULL COMMENT '开始时间',
    end_time            DATETIME        NULL COMMENT '结束时间',
    surgeon_id          BIGINT UNSIGNED NOT NULL COMMENT '主刀医生',
    anesthetist_id      BIGINT UNSIGNED NULL COMMENT '麻醉医生',
    circulating_nurse_id BIGINT UNSIGNED NULL COMMENT '巡回护士',
    scrub_nurse_id      BIGINT UNSIGNED NULL COMMENT '洗手护士',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '1已排台 2已完成 3已取消',
    created_by          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_no (schedule_no),
    UNIQUE KEY uk_schedule_req (request_id),
    UNIQUE KEY uk_schedule_slot (room_id, surgery_date, seq_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '手术排台';

CREATE TABLE or_check_record (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id  BIGINT UNSIGNED NOT NULL COMMENT '手术申请',
    check_type  TINYINT         NOT NULL COMMENT '1麻醉前 2切皮前 3离室前',
    check_items TEXT            NOT NULL COMMENT '核查项JSON[{"item":"","result":true}]',
    checker1_id BIGINT UNSIGNED NOT NULL COMMENT '第一签名人',
    checker2_id BIGINT UNSIGNED NOT NULL COMMENT '第二签名人(须不同人)',
    checked_at  DATETIME        NOT NULL COMMENT '核查时间',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_check_req_type (request_id, check_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '手术安全核查单';

CREATE TABLE or_anesthesia_record (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_no         VARCHAR(32)     NOT NULL COMMENT '麻醉记录号(前缀MZ)',
    request_id        BIGINT UNSIGNED NOT NULL COMMENT '手术申请',
    anesthesia_method TINYINT         NOT NULL COMMENT '1全麻 2椎管内 3神经阻滞 4局麻+镇静 5基础麻醉',
    asa_grade         TINYINT         NOT NULL COMMENT 'ASA分级1~5',
    anesthetist_id    BIGINT UNSIGNED NOT NULL COMMENT '麻醉医生',
    start_time        DATETIME        NULL COMMENT '麻醉开始',
    end_time          DATETIME        NULL COMMENT '麻醉结束',
    drug_note         VARCHAR(512)    NULL COMMENT '术中用药摘要',
    event_note        VARCHAR(512)    NULL COMMENT '术中事件',
    vital_sample      VARCHAR(1024)   NULL COMMENT '关键时点生命体征JSON',
    status            TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效',
    created_by        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version           INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_anes_no (record_no),
    UNIQUE KEY uk_anes_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '麻醉记录';

CREATE TABLE or_postop_record (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id     BIGINT UNSIGNED NOT NULL COMMENT '手术申请',
    recovery_score TINYINT         NOT NULL COMMENT '苏醒评分0~10',
    destination    TINYINT         NOT NULL COMMENT '离室去向 1回病房 2转ICU 3离院',
    followup_note  VARCHAR(256)    NULL COMMENT '术后随访',
    visited_at     DATETIME        NULL COMMENT '随访时间',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_postop_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '术后记录';

-- ----------------------------
-- RIS 影像
-- ----------------------------
CREATE TABLE ris_request (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_no   VARCHAR(32)     NOT NULL COMMENT '检查申请号(前缀JC)',
    order_id     BIGINT UNSIGNED NOT NULL COMMENT '来源检查医嘱',
    admission_id BIGINT UNSIGNED NOT NULL COMMENT '住院',
    patient_id   BIGINT UNSIGNED NOT NULL COMMENT '患者',
    doctor_id    BIGINT UNSIGNED NOT NULL COMMENT '开单医生',
    modality     TINYINT         NOT NULL COMMENT '1DR 2CT 3MR 4超声 5心电',
    body_part    VARCHAR(32)     NOT NULL DEFAULT '通用' COMMENT '检查部位',
    requirement  VARCHAR(256)    NULL COMMENT '临床要求',
    urgency      TINYINT         NOT NULL DEFAULT 1 COMMENT '1常规 2急查',
    status       TINYINT         NOT NULL DEFAULT 10 COMMENT '10待预约 20已预约 30检查中 40已报告 50作废',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ris_req_no (request_no),
    KEY idx_risreq_adm (admission_id, status),
    KEY idx_risreq_status (status),
    KEY idx_risreq_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '检查申请单';

CREATE TABLE ris_device (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_no   VARCHAR(32)     NOT NULL COMMENT '设备编号',
    device_name VARCHAR(64)     NOT NULL COMMENT '设备名称',
    modality    TINYINT         NOT NULL COMMENT '1DR 2CT 3MR',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1可用 0维护',
    created_by  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_no (device_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '影像设备';

CREATE TABLE ris_appointment (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id BIGINT UNSIGNED NOT NULL COMMENT '检查申请',
    device_id  BIGINT UNSIGNED NOT NULL COMMENT '设备',
    appt_time  DATETIME        NOT NULL COMMENT '预约时间',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1已预约 2已到场 3已取消',
    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ris_appt_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '检查预约';

CREATE TABLE ris_image (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    study_no        VARCHAR(32)     NOT NULL COMMENT '影像号(前缀IM)',
    request_id      BIGINT UNSIGNED NOT NULL COMMENT '检查申请',
    series_count    INT             NOT NULL DEFAULT 0 COMMENT '序列数(Mock)',
    image_count     INT             NOT NULL DEFAULT 0 COMMENT '影像张数(Mock)',
    impression_text VARCHAR(1024)   NULL COMMENT 'Mock影像所见',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1已归档',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_study_no (study_no),
    UNIQUE KEY uk_ris_img_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '影像(Mock元数据)';

CREATE TABLE ris_report (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    report_no      VARCHAR(32)     NOT NULL COMMENT '影像报告号(前缀XD)',
    request_id     BIGINT UNSIGNED NOT NULL COMMENT '检查申请',
    finding        VARCHAR(2048)   NOT NULL COMMENT '影像所见',
    conclusion     VARCHAR(1024)   NOT NULL COMMENT '诊断意见',
    critical_sign  VARCHAR(128)    NULL COMMENT '危急征象描述',
    critical_flag  TINYINT         NOT NULL DEFAULT 0 COMMENT '1危急',
    reporter_id    BIGINT UNSIGNED NOT NULL COMMENT '书写医生',
    report_time    DATETIME        NOT NULL COMMENT '书写时间',
    reviewer_id    BIGINT UNSIGNED NULL COMMENT '审核医生(双签)',
    review_time    DATETIME        NULL COMMENT '审核时间',
    status         TINYINT         NOT NULL DEFAULT 10 COMMENT '10书写中 20已发布 30已驳回',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ris_report_no (report_no),
    UNIQUE KEY uk_ris_report_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '影像报告';

-- ----------------------------
-- EMC 急诊五大中心
-- ----------------------------
CREATE TABLE emc_triage (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    triage_no       VARCHAR(32)     NOT NULL COMMENT '分诊单号(前缀FZ)',
    patient_id      BIGINT UNSIGNED NOT NULL COMMENT '患者',
    visit_id        BIGINT UNSIGNED NULL COMMENT '关联门诊就诊(可后补)',
    chief_complaint VARCHAR(256)    NOT NULL COMMENT '主诉',
    body_temp       DECIMAL(4, 1)   NULL COMMENT '体温℃',
    pulse           INT             NULL COMMENT '脉搏次/分',
    respiration     INT             NULL COMMENT '呼吸次/分',
    blood_pressure  VARCHAR(16)     NULL COMMENT '血压 如120/80',
    spo2            INT             NULL COMMENT '血氧%',
    triage_level    TINYINT         NOT NULL COMMENT '1濒危 2危重 3急症 4非急症',
    center_type     TINYINT         NOT NULL DEFAULT 0 COMMENT '0无 1胸痛 2卒中 3创伤 4危重孕产妇 5危重新生儿',
    green_channel   TINYINT         NOT NULL DEFAULT 0 COMMENT '1绿色通道',
    triage_nurse_id BIGINT UNSIGNED NOT NULL COMMENT '分诊护士',
    triage_time     DATETIME        NOT NULL COMMENT '分诊时间',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1已分诊 2已关档',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_triage_no (triage_no),
    KEY idx_triage_status (status, triage_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '急诊分诊';

CREATE TABLE emc_visit (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    visit_no     VARCHAR(32)     NOT NULL COMMENT '五大中心登记号(前缀JZ)',
    triage_id    BIGINT UNSIGNED NOT NULL COMMENT '分诊单',
    center_type  TINYINT         NOT NULL COMMENT '1胸痛 2卒中 3创伤 4危重孕产妇 5危重新生儿',
    doctor_id    BIGINT UNSIGNED NULL COMMENT '责任医生',
    patient_id   BIGINT UNSIGNED NOT NULL COMMENT '患者',
    admission_id BIGINT UNSIGNED NULL COMMENT '关联住院(可后补)',
    visit_id     BIGINT UNSIGNED NULL COMMENT '关联门诊就诊(可后补)',
    start_time   DATETIME        NOT NULL COMMENT '登记时刻(时限基准)',
    outcome      TINYINT         NULL COMMENT '转归 1收住院 2急诊手术 3转院 4离院 5死亡',
    outcome_time DATETIME        NULL COMMENT '转归时间',
    status       TINYINT         NOT NULL DEFAULT 10 COMMENT '10救治中 20已关档',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emc_visit_no (visit_no),
    KEY idx_emc_visit_status (status, start_time),
    KEY idx_emc_visit_center (center_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '五大中心登记';

CREATE TABLE emc_timepoint (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    emc_visit_id BIGINT UNSIGNED NOT NULL COMMENT '五大中心登记',
    node_code    VARCHAR(32)     NOT NULL COMMENT '节点编码(字典)',
    node_time    DATETIME        NOT NULL COMMENT '节点时刻',
    recorder_id  BIGINT UNSIGNED NOT NULL COMMENT '记录人',
    note         VARCHAR(256)    NULL COMMENT '备注',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emc_tp (emc_visit_id, node_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '五大中心时间节点';

CREATE TABLE emc_node_dict (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    node_code      VARCHAR(32)     NOT NULL COMMENT '节点编码',
    node_name      VARCHAR(64)     NOT NULL COMMENT '节点名称',
    center_type    TINYINT         NOT NULL COMMENT '1胸痛 2卒中 3创伤 4危重孕产妇 5危重新生儿',
    seq_no         INT             NOT NULL DEFAULT 1 COMMENT '展示序号',
    target_minutes INT             NULL COMMENT '自登记时刻目标分钟(NULL不限时)',
    status         TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emc_node_code (node_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '五大中心节点字典';

-- 收费类别扩展：9 手术费 10 麻醉费（《15》§4，无表结构变更仅注释）
ALTER TABLE bas_charge_item
    MODIFY COLUMN category TINYINT NOT NULL
    COMMENT '1挂号费 2诊查费 3检查费 4检验费 5治疗费 6材料费 7药品费 8床位费 9手术费 10麻醉费';
