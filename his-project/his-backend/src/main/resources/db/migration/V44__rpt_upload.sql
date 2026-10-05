-- 五期-lite（八十二轮）：区域卫生信息平台上报表（Mock 网关版）。
-- 业务闭环（传染病回执/病案归档/出院结算）触发入队；投递器按状态推进：
--   10 待上报 → 20 已上报（记受理号）| 30 上报失败（重试耗尽，可手动重报回 10）
-- payload 为标准化报文（类 FHIR 简化包：患者脱敏身份 + 事件编码 + 机构 + 业务摘要）。
CREATE TABLE rpt_upload (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    upload_no     VARCHAR(32)  NOT NULL COMMENT '上报号 RS+日期+序号',
    biz_type      TINYINT      NOT NULL COMMENT '业务类型 1传染病报告卡 2病案归档 3出院结算',
    biz_id        BIGINT       NOT NULL COMMENT '业务单 ID',
    biz_no        VARCHAR(32)  NOT NULL COMMENT '业务单号（溯源）',
    payload       LONGTEXT     NOT NULL COMMENT '标准化报文 JSON',
    status        TINYINT      NOT NULL DEFAULT 10 COMMENT '10 待上报 20 已上报 30 失败',
    receipt_no    VARCHAR(32)  NULL COMMENT '区域平台受理号',
    retry_count   INT          NOT NULL DEFAULT 0,
    last_error    VARCHAR(512) NULL,
    uploaded_at   DATETIME     NULL,
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_rpt_biz (biz_type, biz_id),
    UNIQUE KEY uk_rpt_no (upload_no),
    INDEX idx_rpt_status (status, retry_count),
    INDEX idx_rpt_time (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '区域平台上报表';
