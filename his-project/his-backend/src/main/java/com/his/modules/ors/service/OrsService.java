package com.his.modules.ors.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.modules.basedata.app.BasedataAppService;
import com.his.modules.basedata.app.ChargeItemDTO;
import com.his.modules.inp.app.InpAppService;
import com.his.modules.ors.dto.*;
import com.his.modules.ors.entity.*;
import com.his.modules.ors.mapper.*;
import com.his.modules.plt.service.PltService;
import com.his.infrastructure.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ORIS 手术麻醉闭环服务（《15》§2.1）：申请 → 审核 → 排台 → 三方核查 → 手术 → 复苏 → 关档记账。
 * 手术申请单制（独立于 doc 医嘱状态机）；费用经 InpAppService.addExecFee 统一通道。
 */
@Service
@RequiredArgsConstructor
public class OrsService {
    private final OrsSurgeryRequestMapper requestMapper;
    private final OrsScheduleMapper scheduleMapper;
    private final OrsCheckRecordMapper checkMapper;
    private final OrsAnesthesiaRecordMapper anesthesiaMapper;
    private final OrsPostopRecordMapper postopMapper;
    private final OrsOperateRoomMapper roomMapper;
    private final InpAppService inpAppService;
    private final BasedataAppService basedataAppService;
    private final com.his.modules.system.app.SystemAppService systemAppService;
    private final PltService pltService;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    /** 手术申请分页（OR-02） */
    public PageResult<OrsSurgeryRequest> page(OrsRequestQuery query) {
        Page<OrsSurgeryRequest> page = requestMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<OrsSurgeryRequest>()
                        .eq(query.getAdmissionId() != null, OrsSurgeryRequest::getAdmissionId, query.getAdmissionId())
                        .eq(query.getPatientId() != null, OrsSurgeryRequest::getPatientId, query.getPatientId())
                        .eq(query.getStatus() != null, OrsSurgeryRequest::getStatus, query.getStatus())
                        .eq(query.getPlannedDate() != null, OrsSurgeryRequest::getPlannedDate, query.getPlannedDate())
                        .orderByDesc(OrsSurgeryRequest::getId));
        return PageResult.of(page);
    }

    /** 申请详情聚合（OR-03）：申请 + 排台 + 核查单 + 麻醉 + 术后 */
    public Map<String, Object> detail(Long id) {
        OrsSurgeryRequest request = requireRequest(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request", request);
        result.put("schedule", scheduleMapper.selectOne(new LambdaQueryWrapper<OrsSchedule>()
                .eq(OrsSchedule::getRequestId, id).last("LIMIT 1")));
        result.put("checks", checkMapper.selectList(new LambdaQueryWrapper<OrsCheckRecord>()
                .eq(OrsCheckRecord::getRequestId, id).orderByAsc(OrsCheckRecord::getCheckType)));
        result.put("anesthesia", anesthesiaMapper.selectOne(new LambdaQueryWrapper<OrsAnesthesiaRecord>()
                .eq(OrsAnesthesiaRecord::getRequestId, id).last("LIMIT 1")));
        result.put("postop", postopMapper.selectOne(new LambdaQueryWrapper<OrsPostopRecord>()
                .eq(OrsPostopRecord::getRequestId, id).last("LIMIT 1")));
        return result;
    }

    /** 创建手术申请（OR-01）：在院校验 + 归属校验 + 价目快照（类别 9/10 校验） */
    @Transactional
    public String create(SurgeryCreateRequest req) {
        var admission = inpAppService.requireInHospital(req.getAdmissionId());
        if (admission.getPatientId() == null || !admission.getPatientId().equals(req.getPatientId())) {
            throw new BizException(ErrorCode.A0001, "患者与住院登记不匹配，禁止跨患者挂单");
        }
        validateAnesthesiaMethod(req.getAnesthesiaMethod());
        OrsSurgeryRequest request = new OrsSurgeryRequest();
        request.setRequestNo(idGenerator.next("SS"));
        request.setAdmissionId(req.getAdmissionId());
        request.setPatientId(req.getPatientId());
        request.setApplicantDoctorId(CurrentUser.id());
        request.setSurgeryName(req.getSurgeryName());
        request.setSurgeryCode(req.getSurgeryCode());
        request.setDiagnosis(req.getDiagnosis());
        request.setPlannedDate(req.getPlannedDate());
        request.setAnesthesiaMethod(req.getAnesthesiaMethod());
        request.setStatus(10);
        request.setChargeStatus(0);
        if (req.getSurgeryItemId() != null) {
            ChargeItemDTO item = requireChargeItem(req.getSurgeryItemId(), 9, "手术费项目类别不正确");
            request.setSurgeryItemId(item.getId());
            request.setSurgeryPrice(item.getPrice());
        }
        if (req.getAnesthesiaItemId() != null) {
            ChargeItemDTO item = requireChargeItem(req.getAnesthesiaItemId(), 10, "麻醉费项目类别不正确");
            request.setAnesthesiaItemId(item.getId());
            request.setAnesthesiaPrice(item.getPrice());
        }
        requestMapper.insert(request);
        pltService.recordEvent("ors.request.created", request.getRequestNo(),
                "{\"admissionId\":" + req.getAdmissionId() + "}");
        return request.getRequestNo();
    }

    /** 审核（OR-04）：10 → 20 通过 ｜ 10 → 70 驳回（原因留痕） */
    @Transactional
    public void review(Long id, ReviewRequest req) {
        OrsSurgeryRequest request = requireRequest(id);
        if (request.getStatus() != 10) {
            throw new BizException(ErrorCode.A0001, "申请不在待审核状态");
        }
        boolean approved = Boolean.TRUE.equals(req.getApproved());
        request.setStatus(approved ? 20 : 70);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
        if (!approved) {
            pltService.recordEvent("ors.request.rejected", request.getRequestNo(),
                    "{\"reason\":\"" + escapeJson(req.getReason()) + "\"}");
        }
    }

    /** 排台（OR-05）：20 → 30，同手术间同日同台次唯一 */
    @Transactional
    public String schedule(Long id, ScheduleRequest req) {
        OrsSurgeryRequest request = requireRequest(id);
        if (request.getStatus() != 20) {
            throw new BizException(ErrorCode.A0001, "申请未审核通过，不能排台");
        }
        OrsOperateRoom room = roomMapper.selectById(req.getRoomId());
        if (room == null || room.getStatus() == null || room.getStatus() != 1) {
            throw new BizException(ErrorCode.A0001, "手术间不可用");
        }
        Long clash = scheduleMapper.selectCount(new LambdaQueryWrapper<OrsSchedule>()
                .eq(OrsSchedule::getRoomId, req.getRoomId())
                .eq(OrsSchedule::getSurgeryDate, req.getSurgeryDate())
                .eq(OrsSchedule::getSeqNo, req.getSeqNo())
                .eq(OrsSchedule::getStatus, 1));
        if (clash != null && clash > 0) {
            throw new BizException(ErrorCode.A0001, "该手术间台次已被占用");
        }
        OrsSchedule schedule = new OrsSchedule();
        schedule.setScheduleNo(idGenerator.next("PT"));
        schedule.setRequestId(id);
        schedule.setRoomId(req.getRoomId());
        schedule.setSurgeryDate(req.getSurgeryDate());
        schedule.setSeqNo(req.getSeqNo());
        schedule.setSlotActive(1); // 占用槽位（完成/取消时释放，可重排）
        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setSurgeonId(req.getSurgeonId());
        schedule.setAnesthetistId(req.getAnesthetistId());
        schedule.setCirculatingNurseId(req.getCirculatingNurseId());
        schedule.setScrubNurseId(req.getScrubNurseId());
        schedule.setStatus(1);
        try {
            scheduleMapper.insert(schedule);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "该手术间台次已被占用");
        }
        request.setStatus(30);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
        pltService.recordEvent("ors.request.scheduled", request.getRequestNo(),
                "{\"room\":\"" + room.getRoomNo() + "\",\"seq\":" + req.getSeqNo() + "}");
        return schedule.getScheduleNo();
    }

    /** 提交核查单（OR-06）：双人签名，全部通过方可登记 */
    @Transactional
    public Long submitCheck(Long id, CheckSubmitRequest req) {
        OrsSurgeryRequest request = requireRequest(id);
        if (req.getCheckType() <= 2) {
            if (request.getStatus() != 30 && request.getStatus() != 40) {
                throw new BizException(ErrorCode.A0001, "麻醉前/切皮前核查须在排台后或术中提交");
            }
        } else if (request.getStatus() != 50) {
            throw new BizException(ErrorCode.A0001, "离室前核查须在复苏中提交");
        }
        if (req.getChecker2Id().equals(CurrentUser.id())) {
            throw new BizException(ErrorCode.A0001, "双人签名须为不同人员");
        }
        // 第二签名人必须真实存在（防任意 ID 冒签）
        if (systemAppService.getUsername(req.getChecker2Id()) == null) {
            throw new BizException(ErrorCode.A0001, "第二签名人不存在");
        }
        for (CheckSubmitRequest.CheckItem item : req.getItems()) {
            if (!Boolean.TRUE.equals(item.getResult())) {
                throw new BizException(ErrorCode.A0001, "存在未通过核查项：" + item.getItem() + "，不得登记核查单");
            }
        }
        OrsCheckRecord record = new OrsCheckRecord();
        record.setRequestId(id);
        record.setCheckType(req.getCheckType());
        try {
            record.setCheckItems(objectMapper.writeValueAsString(req.getItems()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException(ErrorCode.A0001, "核查项序列化失败");
        }
        record.setChecker1Id(CurrentUser.id());
        record.setChecker2Id(req.getChecker2Id());
        record.setCheckedAt(LocalDateTime.now());
        try {
            checkMapper.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.A0001, "该类核查单已提交");
        }
        return record.getId();
    }

    /** 开始手术（OR-07）：30 → 40，硬门禁——麻醉前+切皮前核查齐备 */
    @Transactional
    public void start(Long id) {
        OrsSurgeryRequest request = requireRequest(id);
        if (request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "手术未排台或已开始，不能开始手术");
        }
        requireCheck(id, 1, "麻醉前核查未完成，禁止开始手术");
        requireCheck(id, 2, "切皮前核查未完成，禁止开始手术");
        request.setStatus(40);
        request.setIncisionTime(LocalDateTime.now());
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "手术状态已变化，请刷新后重试");
        }
        OrsSchedule schedule = scheduleMapper.selectOne(new LambdaQueryWrapper<OrsSchedule>()
                .eq(OrsSchedule::getRequestId, id).last("LIMIT 1"));
        if (schedule != null) {
            schedule.setStartTime(LocalDateTime.now());
            scheduleMapper.updateById(schedule);
        }
        pltService.recordEvent("ors.request.started", request.getRequestNo(), "{}");
    }

    /** 麻醉记录（OR-08）：术中保存/覆盖 */
    @Transactional
    public String saveAnesthesia(Long id, AnesthesiaRequest req) {
        OrsSurgeryRequest request = requireRequest(id);
        if (request.getStatus() != 40) {
            throw new BizException(ErrorCode.A0001, "仅术中可保存麻醉记录");
        }
        OrsAnesthesiaRecord record = anesthesiaMapper.selectOne(new LambdaQueryWrapper<OrsAnesthesiaRecord>()
                .eq(OrsAnesthesiaRecord::getRequestId, id).last("LIMIT 1"));
        if (record == null) {
            record = new OrsAnesthesiaRecord();
            record.setRecordNo(idGenerator.next("MZ"));
            record.setRequestId(id);
            record.setAnesthesiaMethod(req.getAnesthesiaMethod() == null
                    ? request.getAnesthesiaMethod() : req.getAnesthesiaMethod());
            record.setStatus(1);
        } else if (req.getAnesthesiaMethod() != null) {
            record.setAnesthesiaMethod(req.getAnesthesiaMethod());
        }
        record.setAsaGrade(req.getAsaGrade());
        record.setAnesthetistId(CurrentUser.id());
        record.setStartTime(req.getStartTime());
        record.setEndTime(req.getEndTime());
        record.setDrugNote(req.getDrugNote());
        record.setEventNote(req.getEventNote());
        record.setVitalSample(req.getVitalSample());
        if (record.getId() == null) {
            try {
                anesthesiaMapper.insert(record);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BizException(ErrorCode.A0001, "该手术已存在麻醉记录");
            }
        } else {
            anesthesiaMapper.updateById(record);
        }
        return record.getRecordNo();
    }

    /** 手术结束（OR-09）：40 → 50 复苏中 */
    @Transactional
    public void finish(Long id) {
        OrsSurgeryRequest request = requireRequest(id);
        if (request.getStatus() != 40) {
            throw new BizException(ErrorCode.A0001, "手术不在术中状态");
        }
        // 五十九轮：结束手术前麻醉记录必须存在（临床完整性，与一致性巡检 inconsistent_done_no_anesthesia 对齐）
        OrsAnesthesiaRecord record = anesthesiaMapper.selectOne(new LambdaQueryWrapper<OrsAnesthesiaRecord>()
                .eq(OrsAnesthesiaRecord::getRequestId, id).last("LIMIT 1"));
        if (record == null) {
            throw new BizException(ErrorCode.A0001, "结束手术前须保存麻醉记录");
        }
        request.setStatus(50);
        request.setEndTime(LocalDateTime.now());
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "手术状态已变化，请刷新后重试");
        }
        if (record.getEndTime() == null) {
            record.setEndTime(LocalDateTime.now());
            anesthesiaMapper.updateById(record);
        }
        OrsSchedule schedule = scheduleMapper.selectOne(new LambdaQueryWrapper<OrsSchedule>()
                .eq(OrsSchedule::getRequestId, id).last("LIMIT 1"));
        if (schedule != null) {
            schedule.setEndTime(LocalDateTime.now());
            schedule.setStatus(2);
            // 显式 UPDATE 置 NULL（updateById 默认忽略 null 字段——释放不生效，查验三十一轮）
            scheduleMapper.update(null, new LambdaUpdateWrapper<OrsSchedule>()
                    .eq(OrsSchedule::getId, schedule.getId())
                    .set(OrsSchedule::getSlotActive, null)
                    .set(OrsSchedule::getStatus, 2)
                    .set(OrsSchedule::getEndTime, LocalDateTime.now()));
        }
    }

    /** 离室登记（OR-10）：50 → 60，硬门禁——离室前核查 + 术后记录 */
    @Transactional
    public void leave(Long id, LeaveRequest req) {
        OrsSurgeryRequest request = requireRequest(id);
        if (request.getStatus() != 50) {
            throw new BizException(ErrorCode.A0001, "手术不在复苏中状态");
        }
        requireCheck(id, 3, "离室前核查未完成，禁止离室");
        Long exists = postopMapper.selectCount(new LambdaQueryWrapper<OrsPostopRecord>()
                .eq(OrsPostopRecord::getRequestId, id));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.A0001, "术后记录已登记");
        }
        OrsPostopRecord postop = new OrsPostopRecord();
        postop.setRequestId(id);
        postop.setRecoveryScore(req.getRecoveryScore());
        postop.setDestination(req.getDestination());
        postop.setFollowupNote(req.getFollowupNote());
        postop.setStatus(1);
        postopMapper.insert(postop);
        request.setStatus(60);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "手术状态已变化，请刷新后重试");
        }
    }

    /** 完成关档（OR-11）：自动生成手术费/麻醉费（charge_status 乐观防重） */
    @Transactional
    public Map<String, Object> complete(Long id) {
        OrsSurgeryRequest request = requireRequest(id);
        if (request.getStatus() != 60) {
            throw new BizException(ErrorCode.A0001, "手术未离室，不能关档");
        }
        inpAppService.requireInHospital(request.getAdmissionId());
        int updated = requestMapper.update(null, new LambdaUpdateWrapper<OrsSurgeryRequest>()
                .eq(OrsSurgeryRequest::getId, id)
                .eq(OrsSurgeryRequest::getChargeStatus, 0)
                .set(OrsSurgeryRequest::getChargeStatus, 1));
        if (updated != 1) {
            throw new BizException(ErrorCode.A0001, "手术已关档记账，请勿重复操作");
        }
        Map<String, Object> fees = new LinkedHashMap<>();
        if (request.getSurgeryItemId() != null) {
            ChargeItemDTO item = basedataAppService.getChargeItem(request.getSurgeryItemId());
            java.math.BigDecimal price = request.getSurgeryPrice() != null
                    ? request.getSurgeryPrice() : (item == null ? null : item.getPrice());
            if (price == null || price.signum() <= 0) {
                // 价目缺失/为零不产生 0 元脏账，关档返回中如实缺少该笔
                pltService.recordEvent("ors.charge.skipped", request.getRequestNo(),
                        "{\"type\":\"surgery\",\"itemId\":" + request.getSurgeryItemId() + "}");
            } else {
                fees.put("surgeryFee", inpAppService.addExecFee(request.getAdmissionId(), 9,
                        item == null ? request.getSurgeryName() : item.getItemName(),
                        BigDecimal.ONE, price, id));
            }
        }
        if (request.getAnesthesiaItemId() != null) {
            ChargeItemDTO item = basedataAppService.getChargeItem(request.getAnesthesiaItemId());
            java.math.BigDecimal price = request.getAnesthesiaPrice() != null
                    ? request.getAnesthesiaPrice() : (item == null ? null : item.getPrice());
            if (price == null || price.signum() <= 0) {
                pltService.recordEvent("ors.charge.skipped", request.getRequestNo(),
                        "{\"type\":\"anesthesia\",\"itemId\":" + request.getAnesthesiaItemId() + "}");
            } else {
                fees.put("anesthesiaFee", inpAppService.addExecFee(request.getAdmissionId(), 10,
                        item == null ? "麻醉费" : item.getItemName(),
                        BigDecimal.ONE, price, id));
            }
        }
        pltService.recordEvent("ors.request.completed", request.getRequestNo(),
                "{\"fees\":" + fees.size() + "}");
        return fees;
    }

    /** 取消（OR-12）：10/20/30 → 70 */
    @Transactional
    public void cancel(Long id, String reason) {
        OrsSurgeryRequest request = requireRequest(id);
        if (request.getStatus() != 10 && request.getStatus() != 20 && request.getStatus() != 30) {
            throw new BizException(ErrorCode.A0001, "待审核/已审核/已排台状态才可取消");
        }
        request.setStatus(70);
        if (requestMapper.updateById(request) != 1) {
            throw new BizException(ErrorCode.A0001, "申请状态已变化，请刷新后重试");
        }
        if (request.getStatus() == 70) {
            OrsSchedule schedule = scheduleMapper.selectOne(new LambdaQueryWrapper<OrsSchedule>()
                    .eq(OrsSchedule::getRequestId, id).last("LIMIT 1"));
            if (schedule != null && schedule.getStatus() == 1) {
                scheduleMapper.update(null, new LambdaUpdateWrapper<OrsSchedule>()
                        .eq(OrsSchedule::getId, schedule.getId())
                        .set(OrsSchedule::getSlotActive, null)
                        .set(OrsSchedule::getStatus, 3));
            }
        }
        pltService.recordEvent("ors.request.cancelled", request.getRequestNo(),
                "{\"reason\":\"" + escapeJson(reason) + "\"}");
    }

    /** 手术间列表（OR-13） */
    public List<OrsOperateRoom> rooms() {
        return roomMapper.selectList(new LambdaQueryWrapper<OrsOperateRoom>()
                .orderByAsc(OrsOperateRoom::getRoomNo));
    }

    private OrsSurgeryRequest requireRequest(Long id) {
        OrsSurgeryRequest request = requestMapper.selectById(id);
        if (request == null) {
            throw new BizException(ErrorCode.A0001, "手术申请不存在");
        }
        return request;
    }

    private void requireCheck(Long id, int type, String message) {
        Long count = checkMapper.selectCount(new LambdaQueryWrapper<OrsCheckRecord>()
                .eq(OrsCheckRecord::getRequestId, id)
                .eq(OrsCheckRecord::getCheckType, type));
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.A0001, message);
        }
    }

    private ChargeItemDTO requireChargeItem(Long itemId, int category, String message) {
        ChargeItemDTO item = basedataAppService.getChargeItem(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.A0001, "收费项目不存在");
        }
        if (item.getCategory() == null || item.getCategory() != category) {
            throw new BizException(ErrorCode.A0001, message);
        }
        return item;
    }

    private void validateAnesthesiaMethod(Integer method) {
        if (method == null || method < 1 || method > 5) {
            throw new BizException(ErrorCode.A0001, "麻醉方式取值 1~5");
        }
    }

    /** 事件载荷 JSON 字符串转义（五十六轮：原私有实现把 " 替换为 ' 篡改留痕数据，统一委托共享工具保真转义） */
    private String escapeJson(String s) {
        return com.his.infrastructure.util.JsonEscapeUtil.escape(s);
    }
}
