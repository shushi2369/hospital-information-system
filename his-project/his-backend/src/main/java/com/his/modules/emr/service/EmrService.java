package com.his.modules.emr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.infrastructure.security.CurrentUser;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.emr.dto.EmrQcRequest;
import com.his.modules.emr.dto.EmrRecordCreateRequest;
import com.his.modules.emr.dto.EmrRecordQuery;
import com.his.modules.emr.dto.EmrRecordUpdateRequest;
import com.his.modules.emr.dto.EmrTemplateRequest;
import com.his.modules.emr.entity.EmrRecord;
import com.his.modules.emr.entity.EmrTemplate;
import com.his.modules.emr.mapper.EmrRecordMapper;
import com.his.modules.emr.mapper.EmrTemplateMapper;
import com.his.modules.inp.app.InpAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * EMR 服务（E-01~E-08）：结构化书写、提交校验（必填节）、质控闭环（通过/退回）。
 */
@Service
@RequiredArgsConstructor
public class EmrService {
    private final EmrRecordMapper recordMapper;
    private final EmrTemplateMapper templateMapper;
    private final InpAppService inpAppService;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    /** 各文书类型必填节（提交校验 B6201） */
    private static final Map<Integer, List<String>> REQUIRED_SECTIONS = Map.of(
            1, List.of("主诉", "现病史", "查体", "初步诊断"),
            2, List.of("病程记录"),
            3, List.of("诊疗经过", "出院诊断"));

    /** 新建文书（E-01）：入院记录一患一院一条（B6203） */
    @Transactional
    public java.util.Map<String, Object> create(EmrRecordCreateRequest req) {
        inpAppService.requireAdmission(req.getAdmissionId());
        if (req.getDocType() == 1) {
            Long exists = recordMapper.selectCount(new LambdaQueryWrapper<EmrRecord>()
                    .eq(EmrRecord::getAdmissionId, req.getAdmissionId())
                    .eq(EmrRecord::getDocType, 1));
            if (exists != null && exists > 0) {
                throw new BizException(ErrorCode.B6203);
            }
        }
        EmrRecord record = new EmrRecord();
        record.setRecordNo(idGenerator.next("BW"));
        record.setAdmissionId(req.getAdmissionId());
        record.setDocType(req.getDocType());
        record.setTitle(req.getTitle());
        record.setContentJson(toJson(req.getContent()));
        record.setDoctorId(CurrentUser.id());
        record.setRecordTime(LocalDateTime.now());
        record.setStatus(10);
        recordMapper.insert(record);
        return Map.of("recordNo", record.getRecordNo(), "id", record.getId());
    }

    /** 暂存（E-02）：仅书写中可改 */
    @Transactional
    public void update(Long recordId, EmrRecordUpdateRequest req) {
        EmrRecord record = requireWritable(recordId);
        if (req.getTitle() != null) {
            record.setTitle(req.getTitle());
        }
        if (req.getContent() != null) {
            record.setContentJson(toJson(req.getContent()));
        }
        recordMapper.updateById(record);
    }

    /** 提交（E-03）：必填节校验（B6201） */
    @Transactional
    public void submit(Long recordId) {
        EmrRecord record = requireWritable(recordId);
        Map<String, Object> content = fromJson(record.getContentJson());
        List<String> required = REQUIRED_SECTIONS.getOrDefault(record.getDocType(), List.of());
        List<String> missing = required.stream()
                .filter(k -> content.get(k) == null || String.valueOf(content.get(k)).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.B6201, "病历必填内容缺失：" + String.join("、", missing));
        }
        record.setStatus(20);
        record.setRecordTime(LocalDateTime.now());
        // 入院记录 24h 时限（《09》§7.1：超时自动标记，不阻断）
        if (record.getDocType() == 1) {
            var admission = inpAppService.getAdmission(record.getAdmissionId());
            if (admission != null && admission.getAdmissionTime() != null
                    && record.getRecordTime().isAfter(admission.getAdmissionTime().plusHours(24))) {
                try {
                    record.setQcIssues(objectMapper.writeValueAsString(
                            List.of("入院记录超24小时提交（系统自动标记）")));
                } catch (Exception ignored) {
                }
            }
        }
        recordMapper.updateById(record);
    }

    /** 质控（E-06）：通过 → 锁定；退回 → 可修改 */
    @Transactional
    public void qc(Long recordId, EmrQcRequest req) {
        EmrRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.A0001, "文书不存在");
        }
        if (record.getStatus() != 20) {
            throw new BizException(ErrorCode.B6202, "文书未提交，不能质控");
        }
        record.setStatus(Boolean.TRUE.equals(req.getPass()) ? 30 : 40);
        record.setQcBy(CurrentUser.id());
        record.setQcTime(LocalDateTime.now());
        try {
            record.setQcIssues(objectMapper.writeValueAsString(req.getIssues() == null ? List.of() : req.getIssues()));
        } catch (Exception ignored) {
            record.setQcIssues("[]");
        }
        recordMapper.updateById(record);
    }

    /** 待质控队列（E-08） */
    public List<EmrRecord> qcPending() {
        return recordMapper.selectList(new LambdaQueryWrapper<EmrRecord>()
                .eq(EmrRecord::getStatus, 20)
                .orderByAsc(EmrRecord::getId)
                .last("LIMIT 100"));
    }

    /** 文书分页（E-04） */
    public PageResult<EmrRecord> page(EmrRecordQuery query) {
        Page<EmrRecord> page = recordMapper.selectPage(query.toPage(),
                new LambdaQueryWrapper<EmrRecord>()
                        .eq(query.getAdmissionId() != null, EmrRecord::getAdmissionId, query.getAdmissionId())
                        .eq(query.getDocType() != null, EmrRecord::getDocType, query.getDocType())
                        .eq(query.getStatus() != null, EmrRecord::getStatus, query.getStatus())
                        .orderByDesc(EmrRecord::getId));
        return PageResult.of(page);
    }

    public EmrRecord detail(Long recordId) {
        EmrRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.A0001, "文书不存在");
        }
        return record;
    }

    // ---------------- 模板 ----------------

    @Transactional
    public Long createTemplate(EmrTemplateRequest req) {
        EmrTemplate template = new EmrTemplate();
        template.setTemplateName(req.getTemplateName());
        template.setDocType(req.getDocType());
        template.setDeptId(req.getDeptId());
        template.setContentJson(toJson(req.getContent()));
        template.setCreatedBy(CurrentUser.id());
        template.setStatus(1);
        templateMapper.insert(template);
        return template.getId();
    }

    /** 模板停用：被文书套用记录不做强关联，直接停用（B6204 仅在删除语义时提示） */
    @Transactional
    public void stopTemplate(Long templateId) {
        EmrTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BizException(ErrorCode.A0001, "模板不存在");
        }
        template.setStatus(0);
        templateMapper.updateById(template);
    }

    public List<EmrTemplate> templates(Integer docType) {
        return templateMapper.selectList(new LambdaQueryWrapper<EmrTemplate>()
                .eq(EmrTemplate::getStatus, 1)
                .eq(docType != null, EmrTemplate::getDocType, docType)
                .orderByDesc(EmrTemplate::getId));
    }

    // ---------------- 内部 ----------------

    private EmrRecord requireWritable(Long recordId) {
        EmrRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.A0001, "文书不存在");
        }
        if (record.getStatus() != 10 && record.getStatus() != 40) {
            throw new BizException(ErrorCode.B6202);
        }
        return record;
    }

    private String toJson(Map<String, Object> content) {
        String json;
        try {
            json = objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new BizException(ErrorCode.A0001, "文书内容格式错误");
        }
        // 资源保护：单份文书上限 64KB，防止异常超大载荷写入
        if (json.length() > 64 * 1024) {
            throw new BizException(ErrorCode.A0001, "文书内容过大（上限 64KB）");
        }
        return json;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
