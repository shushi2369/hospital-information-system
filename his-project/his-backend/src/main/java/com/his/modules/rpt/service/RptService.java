package com.his.modules.rpt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.util.IdGenerator;
import com.his.infrastructure.util.JsonEscapeUtil;
import com.his.modules.plt.service.PltService;
import com.his.modules.rpt.entity.RptUpload;
import com.his.modules.rpt.mapper.RptUploadMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 区域平台上报服务（五期-lite）：业务闭环触发入队，投递器推进状态。
 *
 * 交付模式（rpt.gateway.mode）：
 *  MOCK —— 内置模拟网关（默认）：生成区域受理号（QY 前缀），patientName 含"压测失败"时确定性拒绝（供 e2e/教学演示失败路径）；
 *  HTTP —— POST 到 rpt.gateway.url（真实对接预留，本演示环境未启用）。
 *
 * 状态机：10 待上报 → 20 已上报（记受理号）| 30 失败（重试耗尽，事件告警，可手动重报回队）。
 * 幂等：biz_type + biz_id 唯一索引，重复触发跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RptService {

    private final RptUploadMapper uploadMapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final PltService pltService;

    @Value("${rpt.gateway.mode:MOCK}")
    private String gatewayMode;
    @Value("${rpt.gateway.mock-fail-rate:0.0}")
    private double mockFailRate;
    @Value("${rpt.gateway.url:}")
    private String gatewayUrl;

    /** 入队命令（触发点组装：标准化信封的患者与事件字段） */
    @Getter
    @RequiredArgsConstructor
    public static class EnqueueCmd {
        private final int bizType;
        private final Long bizId;
        private final String bizNo;
        private final Long patientId;
        private final String patientName;
        private final String eventCode;
        private final LocalDateTime occurredAt;
        private final Map<String, Object> summary;
    }

    private static final String[] BIZ_TYPE_NAMES = {"", "传染病报告卡", "病案归档", "出院结算"};

    /** 入队（幂等）：biz_type+biz_id 唯一，重复触发静默返回已存在记录；
     *  并发撞唯一索引时捕获后回读（不能让 DuplicateKey 回滚宿主业务事务，如出院结算） */
    public RptUpload enqueue(EnqueueCmd cmd) {
        RptUpload exists = uploadMapper.selectOne(new LambdaQueryWrapper<RptUpload>()
                .eq(RptUpload::getBizType, cmd.getBizType())
                .eq(RptUpload::getBizId, cmd.getBizId())
                .last("LIMIT 1"));
        if (exists != null) {
            return exists;
        }
        RptUpload up = new RptUpload();
        up.setUploadNo(idGenerator.next("RS"));
        up.setBizType(cmd.getBizType());
        up.setBizId(cmd.getBizId());
        up.setBizNo(cmd.getBizNo());
        up.setPayload(buildPayload(up.getUploadNo(), cmd));
        up.setStatus(RptUpload.STATUS_PENDING);
        up.setRetryCount(0);
        try {
            uploadMapper.insert(up);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return uploadMapper.selectOne(new LambdaQueryWrapper<RptUpload>()
                    .eq(RptUpload::getBizType, cmd.getBizType())
                    .eq(RptUpload::getBizId, cmd.getBizId())
                    .last("LIMIT 1"));
        }
        pltService.recordEvent("rpt.upload.enqueued", up.getUploadNo(),
                "{\"bizType\":" + cmd.getBizType() + ",\"bizNo\":\"" + JsonEscapeUtil.escape(cmd.getBizNo()) + "\"}");
        return up;
    }

    /** 定时投递：每 30 秒扫一批待上报（每批 50 条，单条独立 try 防互相拖垮） */
    @Scheduled(fixedDelay = 30_000)
    public void deliverPendingScheduled() {
        deliverBatch(50);
    }

    /** 手动触发一批投递（管理端/e2e 用），返回本批成功数。limit 钳制防一次拖垮调度线程 */
    public int deliverBatch(int limit) {
        if (!"MOCK".equals(gatewayMode) && (gatewayUrl == null || gatewayUrl.isBlank())) {
            return 0;
        }
        int capped = Math.min(Math.max(1, limit), 500);
        List<RptUpload> pending = uploadMapper.selectList(new LambdaQueryWrapper<RptUpload>()
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                .orderByAsc(RptUpload::getId)
                .last("LIMIT " + capped));
        int ok = 0;
        for (RptUpload up : pending) {
            if (deliverOne(up)) {
                ok++;
            }
        }
        return ok;
    }

    /** 单条投递：MOCK 网关受理 / HTTP POST（预留）。成功置 20，失败重试计数。 */
    private boolean deliverOne(RptUpload up) {
        try {
            String receipt;
            if ("HTTP".equals(gatewayMode) && gatewayUrl != null && !gatewayUrl.isBlank()) {
                receipt = httpDeliver(up);
            } else {
                receipt = mockAccept(up);
            }
            markUploaded(up.getId(), receipt);
            return true;
        } catch (Exception e) {
            markFailure(up, e.getMessage());
            return false;
        }
    }

    /** MOCK 网关：生成区域受理号；患者名含"压测失败"时确定性拒绝（演示/教学失败路径） */
    private String mockAccept(RptUpload up) {
        // 失败标记在报文内（patient.name 含"压测失败"）——从 payload 判定，跨重载稳定
        if (up.getPayload() != null && up.getPayload().contains("压测失败")) {
            throw new IllegalStateException("模拟网关拒绝：压测失败标记");
        }
        return idGenerator.next("QY");
    }

    /** HTTP 网关（真实对接预留）：当前演示环境未启用 */
    private String httpDeliver(RptUpload up) {
        throw new IllegalStateException("HTTP 网关未启用（rpt.gateway.mode=MOCK）");
    }

    private void markUploaded(Long id, String receipt) {
        uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                .eq(RptUpload::getId, id)
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                .set(RptUpload::getStatus, RptUpload.STATUS_UPLOADED)
                .set(RptUpload::getReceiptNo, receipt)
                .set(RptUpload::getUploadedAt, LocalDateTime.now())
                .setSql("retry_count = 0"));
        pltService.recordEvent("rpt.upload.accepted", receipt, "{}");
    }

    private void markFailure(RptUpload up, String error) {
        int retried = uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                .eq(RptUpload::getId, up.getId())
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                .setSql("retry_count = retry_count + 1")
                .set(RptUpload::getLastError, truncate(error)));
        if (retried != 1) {
            return;
        }
        int count = (up.getRetryCount() == null ? 0 : up.getRetryCount()) + 1;
        if (count >= RptUpload.MAX_RETRY) {
            uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                    .eq(RptUpload::getId, up.getId())
                    .set(RptUpload::getStatus, RptUpload.STATUS_FAILED)
                    .set(RptUpload::getLastError, truncate(error)));
            pltService.recordEvent("rpt.upload.failed", up.getUploadNo(),
                    "{\"retry\":" + count + ",\"error\":\"" + JsonEscapeUtil.escape(truncate(error)) + "\"}");
        }
    }

    /** 分页查询（管理端）。pageSize 钳制 ≤200：防 pageSize=10^7 一次拖全表 */
    public PageResult<RptUpload> page(Integer bizType, Integer status, long pageNum, long pageSize) {
        long pn = Math.max(1, pageNum);
        long ps = Math.min(Math.max(1, pageSize), 200);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RptUpload> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pn, ps);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RptUpload> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RptUpload>()
                        .eq(bizType != null, RptUpload::getBizType, bizType)
                        .eq(status != null, RptUpload::getStatus, status)
                        .orderByDesc(RptUpload::getId);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RptUpload> result =
                uploadMapper.selectPage(page, qw);
        PageResult<RptUpload> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setList(result.getRecords());
        return pr;
    }

    public RptUpload detail(Long id) {
        RptUpload up = uploadMapper.selectById(id);
        if (up == null) {
            throw new BizException(ErrorCode.A0001, "上报记录不存在");
        }
        return up;
    }

    /** 统计头（管理端页首）：总数/已上报/待上报/失败 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("total", uploadMapper.selectCount(null));
        m.put("uploaded", uploadMapper.selectCount(new LambdaQueryWrapper<RptUpload>()
                .eq(RptUpload::getStatus, RptUpload.STATUS_UPLOADED)));
        m.put("pending", uploadMapper.selectCount(new LambdaQueryWrapper<RptUpload>()
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)));
        m.put("failed", uploadMapper.selectCount(new LambdaQueryWrapper<RptUpload>()
                .eq(RptUpload::getStatus, RptUpload.STATUS_FAILED)));
        return m;
    }

    /** 手动重报：失败单回队并立即尝试投递一次（管理端按钮/e2e 断言用） */
    public RptUpload retryNow(Long id) {
        RptUpload up = uploadMapper.selectById(id);
        if (up == null) {
            throw new BizException(ErrorCode.A0001, "上报记录不存在");
        }
        if (up.getStatus() == RptUpload.STATUS_UPLOADED) {
            throw new BizException(ErrorCode.A0001, "该上报已成功，无需重报");
        }
        uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                .eq(RptUpload::getId, id)
                .eq(RptUpload::getStatus, RptUpload.STATUS_FAILED)
                .set(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                .set(RptUpload::getRetryCount, 0));
        RptUpload reloaded = uploadMapper.selectById(id);
        deliverOne(reloaded);
        return uploadMapper.selectById(id);
    }

    /** 标准化信封：区域协同报文（类 FHIR 简化包：机构 + 患者脱敏身份 + 事件 + 业务摘要） */
    private String buildPayload(String uploadNo, EnqueueCmd cmd) {
        try {
            String typeJson = objectMapper.writeValueAsString(cmd.getSummary() == null ? Map.of() : cmd.getSummary());
            String body = "{\"uploadNo\":\"" + uploadNo + "\""
                    + ",\"bizType\":" + cmd.getBizType()
                    + ",\"bizTypeName\":\"" + bizTypeName(cmd.getBizType()) + "\""
                    + ",\"bizNo\":\"" + JsonEscapeUtil.escape(cmd.getBizNo()) + "\""
                    + ",\"org\":{\"code\":\"HIS-DEMO\",\"name\":\"演示医院\"}"
                    + ",\"patient\":{\"name\":\"" + JsonEscapeUtil.escape(cmd.getPatientName()) + "\""
                    + ",\"patientId\":" + cmd.getPatientId() + "}"
                    + ",\"event\":{\"code\":\"" + JsonEscapeUtil.escape(cmd.getEventCode())
                    + "\",\"occurredAt\":\"" + cmd.getOccurredAt() + "\"}"
                    + ",\"data\":" + typeJson + "}";
            return body;
        } catch (Exception e) {
            // 报文序列化失败不该阻断入队——降级为最小信封
            return "{\"uploadNo\":\"" + uploadNo + "\",\"serializeError\":true}";
        }
    }

    private String bizTypeName(int bizType) {
        return bizType > 0 && bizType < BIZ_TYPE_NAMES.length ? BIZ_TYPE_NAMES[bizType] : "未知";
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 480 ? s : s.substring(0, 480);
    }
}
