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

    /** 定时投递：每 30 秒扫一批待上报（每批 50 条，单条独立 try 防互相拖垮）。
     *  调度通道尊重失败退避（next_retry_at）：坏数据让位，新上报首投不被队头阻塞（V50） */
    @Scheduled(fixedDelay = 30_000)
    public void deliverPendingScheduled() {
        if (!"MOCK".equals(gatewayMode) && (gatewayUrl == null || gatewayUrl.isBlank())) {
            return;
        }
        List<RptUpload> pending = uploadMapper.selectList(new LambdaQueryWrapper<RptUpload>()
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                // and() 内两条件默认 AND 连接：NULL <= ? 恒 UNKNOWN → 调度器永远零投递（自查审计 P1-2）
                .and(w -> w.isNull(RptUpload::getNextRetryAt).or().le(RptUpload::getNextRetryAt, LocalDateTime.now()))
                .orderByAsc(RptUpload::getId)
                .last("LIMIT 50"));
        for (RptUpload up : pending) {
            deliverOne(up);
        }
    }

    /** 手动触发一批投递（管理端/e2e 用），返回本批成功数。limit 钳制防一次拖垮调度线程。
     *  优先投"从未投过"的新行（next_retry_at IS NULL）：老失败行靠退避滞留 PENDING 时
     *  不占用新上报的首投窗口（八十八轮实测：退避上线后队头阻塞反而恶化，此为正解） */
    public int deliverBatch(int limit) {
        if (!"MOCK".equals(gatewayMode) && (gatewayUrl == null || gatewayUrl.isBlank())) {
            return 0;
        }
        int capped = Math.min(Math.max(1, limit), 500);
        List<RptUpload> pending = uploadMapper.selectList(new LambdaQueryWrapper<RptUpload>()
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                .isNull(RptUpload::getNextRetryAt)
                .orderByAsc(RptUpload::getId)
                .last("LIMIT " + capped));
        if (pending.size() < capped) {
            java.util.Set<Long> taken = pending.stream().map(RptUpload::getId).collect(java.util.stream.Collectors.toSet());
            List<RptUpload> retriable = uploadMapper.selectList(new LambdaQueryWrapper<RptUpload>()
                    .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                    .isNotNull(RptUpload::getNextRetryAt)
                    .orderByAsc(RptUpload::getId)
                    .last("LIMIT " + (capped - pending.size())));
            for (RptUpload up : retriable) {
                if (taken.add(up.getId())) {
                    pending.add(up);
                }
            }
        }
        int ok = 0;
        for (RptUpload up : pending) {
            // 手动批可越过失败退避（原语义），但抢占仍是条件更新，防与调度器并发双发
            if (deliverOne(up, true)) {
                ok++;
            }
        }
        return ok;
    }

    /** 单条投递：MOCK 网关受理 / HTTP POST（预留）。成功置 20，失败重试计数。 */
    private boolean deliverOne(RptUpload up) {
        return deliverOne(up, false);
    }

    /** force=false 调度投递（尊重退避）；force=true 手动批投递（可越过退避） */
    private boolean deliverOne(RptUpload up, boolean force) {
        if (!claimForDelivery(up, force)) {
            return false;
        }
        try {
            String receipt;
            if ("HTTP".equals(gatewayMode) && gatewayUrl != null && !gatewayUrl.isBlank()) {
                receipt = httpDeliver(up);
            } else {
                receipt = mockAccept(up);
            }
            return markUploaded(up.getId(), receipt);
        } catch (Exception e) {
            markFailure(up, e.getMessage());
            return false;
        }
    }

    /**
     * 投递租约：next_retry_at 复用为"租约到期时间"。
     * 调度通道只领 NULL/已到期行；手动批不受退避限制，但按读取到的 next_retry_at 做乐观校验——
     * 并发的调度/手动批/重报只有一个能抢到同一单，防二次外发。
     */
    private boolean claimForDelivery(RptUpload up, boolean force) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<RptUpload> wrapper = new LambdaUpdateWrapper<RptUpload>()
                .eq(RptUpload::getId, up.getId())
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING);
        if (force) {
            if (up.getNextRetryAt() == null) {
                wrapper.isNull(RptUpload::getNextRetryAt);
            } else {
                wrapper.eq(RptUpload::getNextRetryAt, up.getNextRetryAt());
            }
        } else {
            wrapper.and(w -> w.isNull(RptUpload::getNextRetryAt)
                    .or().le(RptUpload::getNextRetryAt, now));
        }
        int claimed = uploadMapper.update(null, wrapper
                .set(RptUpload::getNextRetryAt, now.plusMinutes(2))
                .set(RptUpload::getUpdatedAt, now));
        return claimed == 1;
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

    private boolean markUploaded(Long id, String receipt) {
        int updated = uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                .eq(RptUpload::getId, id)
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                .set(RptUpload::getStatus, RptUpload.STATUS_UPLOADED)
                .set(RptUpload::getReceiptNo, receipt)
                .set(RptUpload::getUploadedAt, LocalDateTime.now())
                .setSql("retry_count = 0"));
        if (updated != 1) {
            log.warn("上报状态已变化，投递结果未落库 id={}", id);
            return false;
        }
        pltService.recordEvent("rpt.upload.accepted", receipt, "{}");
        return true;
    }

    private void markFailure(RptUpload up, String error) {
        LocalDateTime now = LocalDateTime.now();
        // 先原子 +1 并续租，避免并发投递用旧快照重复计数/在收尾窗口被再次领取
        int retried = uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                .eq(RptUpload::getId, up.getId())
                .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                .setSql("retry_count = retry_count + 1")
                .set(RptUpload::getNextRetryAt, now.plusMinutes(2))
                .set(RptUpload::getLastError, truncate(error)));
        if (retried != 1) {
            return;
        }
        RptUpload fresh = uploadMapper.selectById(up.getId());
        int count = fresh == null || fresh.getRetryCount() == null ? 1 : fresh.getRetryCount();
        if (count >= RptUpload.MAX_RETRY) {
            int failed = uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                    .eq(RptUpload::getId, up.getId())
                    .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                    .ge(RptUpload::getRetryCount, RptUpload.MAX_RETRY)
                    .set(RptUpload::getStatus, RptUpload.STATUS_FAILED)
                    .set(RptUpload::getLastError, truncate(error)));
            if (failed == 1) {
                pltService.recordEvent("rpt.upload.failed", up.getUploadNo(),
                        "{\"retry\":" + count + ",\"error\":\"" + JsonEscapeUtil.escape(truncate(error)) + "\"}");
            }
        } else {
            uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                    .eq(RptUpload::getId, up.getId())
                    .eq(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                    .eq(RptUpload::getRetryCount, count)
                    .set(RptUpload::getNextRetryAt, now.plusSeconds(backoffSeconds(count))));
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

    /** 手动重报：失败单回队并立即尝试投递一次（管理端按钮/e2e 断言用）。
     *  仅 FAILED 可重报：PENDING 单可能正被调度器投递，越过租约再发一次会造成同一单双份外发 */
    public RptUpload retryNow(Long id) {
        RptUpload up = uploadMapper.selectById(id);
        if (up == null) {
            throw new BizException(ErrorCode.A0001, "上报记录不存在");
        }
        if (up.getStatus() == RptUpload.STATUS_UPLOADED) {
            throw new BizException(ErrorCode.A0001, "该上报已成功，无需重报");
        }
        if (up.getStatus() == RptUpload.STATUS_PENDING) {
            throw new BizException(ErrorCode.A0001, "该上报已在待上报队列中，请稍后查看结果");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = uploadMapper.update(null, new LambdaUpdateWrapper<RptUpload>()
                .eq(RptUpload::getId, id)
                .eq(RptUpload::getStatus, RptUpload.STATUS_FAILED)
                .set(RptUpload::getStatus, RptUpload.STATUS_PENDING)
                .set(RptUpload::getRetryCount, 0)
                .set(RptUpload::getLastError, null)
                .set(RptUpload::getNextRetryAt, now)
                .set(RptUpload::getUpdatedAt, now));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0008, "上报状态已变化，请刷新后重试");
        }
        RptUpload reloaded = uploadMapper.selectById(id);
        // 走统一抢占：与并发的手动批/调度器只有一个能领到该单
        deliverOne(reloaded, false);
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

    /** 失败退避秒数：60s 起指数翻倍，封顶 10 分钟（第 5 次失败即转 FAILED，退避只影响 1~4 次） */
    static long backoffSeconds(int retryCountAfterIncrement) {
        long seconds = 60L;
        for (int i = 1; i < retryCountAfterIncrement && seconds < 600; i++) {
            seconds *= 2;
        }
        return Math.min(seconds, 600);
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 480 ? s : s.substring(0, 480);
    }
}
