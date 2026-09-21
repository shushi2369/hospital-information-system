-- =====================================================================
-- HIS V34 法定上报与临床协作（传染病/院感/不良事件/会诊）
-- =====================================================================
CREATE TABLE pub_disease_dict (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    disease_name VARCHAR(64)     NOT NULL COMMENT '传染病名称',
    category     VARCHAR(8)      NOT NULL COMMENT '甲/乙/丙类',
    status       TINYINT         NOT NULL DEFAULT 1,
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_disease_name (disease_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '传染病字典';

CREATE TABLE pub_infectious_card (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    card_no      VARCHAR(32)     NOT NULL COMMENT '报告卡号(前缀CR)',
    visit_id     BIGINT UNSIGNED NULL COMMENT '来源门诊就诊',
    admission_id BIGINT UNSIGNED NULL COMMENT '来源住院',
    patient_id   BIGINT UNSIGNED NOT NULL COMMENT '患者',
    doctor_id    BIGINT UNSIGNED NOT NULL COMMENT '报卡医生',
    disease_name VARCHAR(64)     NOT NULL COMMENT '传染病名称',
    disease_category VARCHAR(8)  NOT NULL COMMENT '甲/乙/丙',
    diagnose_date DATE           NOT NULL COMMENT '诊断日期',
    status       TINYINT         NOT NULL DEFAULT 10 COMMENT '10待报 20已上报 30审核通过 40回执已登记 50退回',
    public_doctor_id BIGINT UNSIGNED NULL COMMENT '公卫审核人',
    report_time  DATETIME        NULL COMMENT '上报时间',
    receipt_no   VARCHAR(64)     NULL COMMENT '疾控回执号(Mock)',
    receipt_time DATETIME        NULL COMMENT '回执时间',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_no (card_no),
    KEY idx_card_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '传染病报告卡';

CREATE TABLE pub_hai_case (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_no       VARCHAR(32)     NOT NULL COMMENT '院感病例号(前缀GR)',
    admission_id  BIGINT UNSIGNED NOT NULL COMMENT '住院',
    patient_id    BIGINT UNSIGNED NOT NULL COMMENT '患者',
    infection_type TINYINT        NOT NULL COMMENT '1呼吸道 2导管相关 3切口 4胃肠道 5其他',
    infection_site VARCHAR(64)    NOT NULL COMMENT '感染部位',
    diagnose_date DATE            NOT NULL COMMENT '诊断日期',
    reporter_id   BIGINT UNSIGNED NOT NULL COMMENT '报告人',
    status        TINYINT         NOT NULL DEFAULT 10 COMMENT '10已报告 20感染科确认 30整改完成',
    confirm_note  VARCHAR(512)    NULL COMMENT '确认/整改说明',
    confirm_time  DATETIME        NULL,
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hai_no (case_no),
    KEY idx_hai_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '院感病例';

CREATE TABLE ae_event (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_no     VARCHAR(32)     NOT NULL COMMENT '不良事件号(前缀AE)',
    event_type   TINYINT         NOT NULL COMMENT '1药品 2跌倒坠床 3器械 4输血 5手术 6院感 7其他',
    severity     TINYINT         NOT NULL COMMENT '1轻 2中 3重 4警讯',
    department_id BIGINT UNSIGNED NOT NULL COMMENT '发生科室',
    event_time   DATETIME        NOT NULL COMMENT '发生时间',
    description  VARCHAR(1024)   NOT NULL COMMENT '事件描述',
    reporter_id  BIGINT UNSIGNED NOT NULL COMMENT '报告人(任何员工)',
    qc_id        BIGINT UNSIGNED NULL COMMENT '质控分派人',
    handler_note VARCHAR(512)    NULL COMMENT '整改说明',
    handler_id   BIGINT UNSIGNED NULL COMMENT '整改人',
    closed_time  DATETIME        NULL COMMENT '闭环时间',
    status       TINYINT         NOT NULL DEFAULT 10 COMMENT '10已上报 20质控分派 30整改中 40已闭环',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ae_no (event_no),
    KEY idx_ae_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '不良事件';

CREATE TABLE cnt_request (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    req_no          VARCHAR(32)     NOT NULL COMMENT '会诊单号(前缀HZ)',
    admission_id    BIGINT UNSIGNED NULL COMMENT '住院(可空=门诊会诊)',
    visit_id        BIGINT UNSIGNED NULL COMMENT '门诊就诊(与住院二选一)',
    patient_id      BIGINT UNSIGNED NOT NULL COMMENT '患者',
    applicant_id    BIGINT UNSIGNED NOT NULL COMMENT '申请医生',
    dept_id         BIGINT UNSIGNED NOT NULL COMMENT '受邀科室',
    consult_doctor_id BIGINT UNSIGNED NULL COMMENT '受邀医生',
    urgent          TINYINT         NOT NULL DEFAULT 0 COMMENT '急会诊',
    reason          VARCHAR(512)    NOT NULL COMMENT '会诊目的',
    status          TINYINT         NOT NULL DEFAULT 10 COMMENT '10已申请 20已接受 30已完成',
    accept_time     DATETIME        NULL COMMENT '接受时间',
    opinion         VARCHAR(1024)   NULL COMMENT '会诊意见',
    opinion_time    DATETIME        NULL COMMENT '意见时间',
    created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cnt_no (req_no),
    KEY idx_cnt_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会诊申请';
