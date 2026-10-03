package com.his.modules.patient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.common.PageResult;
import com.his.common.util.MaskUtil;
import com.his.infrastructure.util.CryptoUtil;
import com.his.infrastructure.util.IdGenerator;
import com.his.modules.patient.dto.PatientCreateRequest;
import com.his.modules.patient.dto.PatientQuery;
import com.his.modules.patient.dto.PatientResponse;
import com.his.modules.patient.dto.PatientUpdateRequest;
import com.his.modules.patient.entity.PatMedicalCard;
import com.his.modules.patient.entity.PatPatient;
import com.his.modules.patient.mapper.PatMedicalCardMapper;
import com.his.modules.patient.mapper.PatPatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 患者档案服务（P-01~P-05）。
 * 身份证密文存储 + SHA-256 摘要唯一校验（《04》§6）；响应一律脱敏。
 */
@Service
@RequiredArgsConstructor
public class PatientService {
    private final PatPatientMapper patientMapper;
    private final PatMedicalCardMapper cardMapper;
    private final com.his.modules.plt.app.PltAppService pltAppService;
    private final CryptoUtil cryptoUtil;
    private final IdGenerator idGenerator;

    @Transactional
    public String create(PatientCreateRequest req) {
        String hash = cryptoUtil.sha256Hex(req.getIdCardNo());
        PatPatient existing = patientMapper.selectByIdCardHash(hash);
        if (existing != null) {
            throw new BizException(ErrorCode.B1001,
                    "该身份证已建档（建档号 " + existing.getPatientNo() + "，患者 " + existing.getName() + "）");
        }
        PatPatient patient = new PatPatient();
        patient.setPatientNo(idGenerator.next("P"));
        patient.setName(req.getName());
        patient.setGender(req.getGender());
        patient.setBirthDate(req.getBirthDate());
        patient.setIdCardNo(cryptoUtil.encrypt(req.getIdCardNo()));
        patient.setIdCardHash(hash);
        patient.setPhone(req.getPhone());
        patient.setAddress(req.getAddress());
        patient.setAllergyHistory(req.getAllergyHistory());
        patient.setPastHistory(req.getPastHistory());
        patient.setStatus(1);
        try {
            patientMapper.insert(patient);
            // EMPI：建档即注册患者主索引（平台层）
            pltAppService.registerMpi(patient.getId());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发建档撞 id_card_hash 唯一索引：预检查窗口兜底
            throw new BizException(ErrorCode.B1001,
                    "该身份证已建档（建档号 " + existing.getPatientNo() + "，患者 " + existing.getName() + "）");
        }
        return patient.getPatientNo();
    }

    public PageResult<PatientResponse> page(PatientQuery query) {
        LambdaQueryWrapper<PatPatient> wrapper = new LambdaQueryWrapper<PatPatient>()
                // 默认仅有效患者：合并/离职停用后源患者不可再被选用（防合并后数据继续分裂）
                .eq(!(Boolean.TRUE.equals(query.getIncludeDisabled())), PatPatient::getStatus, 1)
                .like(query.getName() != null && !query.getName().isBlank(), PatPatient::getName, query.getName())
                // 五十三轮：电话改前缀检索（likeRight 走 idx_patient_phone；全模糊前置通配符索引无效）
                .likeRight(query.getPhone() != null && !query.getPhone().isBlank(), PatPatient::getPhone, query.getPhone())
                .like(query.getPatientNo() != null && !query.getPatientNo().isBlank(), PatPatient::getPatientNo, query.getPatientNo())
                .orderByDesc(PatPatient::getId);
        String idCardNo = query.getIdCardNo();
        if (idCardNo != null && !idCardNo.isBlank()) {
            wrapper.eq(PatPatient::getIdCardHash, cryptoUtil.sha256Hex(idCardNo.trim()));
        }
        if (query.getCardNo() != null && !query.getCardNo().isBlank()) {
            PatMedicalCard card = cardMapper.selectOne(new LambdaQueryWrapper<PatMedicalCard>()
                    .eq(PatMedicalCard::getCardNo, query.getCardNo()).last("LIMIT 1"));
            if (card == null) {
                PageResult<PatientResponse> empty = new PageResult<>();
                empty.setTotal(0);
                empty.setList(java.util.List.of());
                return empty;
            }
            wrapper.eq(PatPatient::getId, card.getPatientId());
        }
        Page<PatPatient> page = patientMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page, this::toResponse);
    }

    public PatientResponse detail(Long id) {
        PatPatient patient = requirePatient(id);
        return toResponse(patient);
    }

    @Transactional
    public void update(Long id, PatientUpdateRequest req) {
        PatPatient patient = requirePatient(id);
        patient.setPhone(req.getPhone());
        patient.setAddress(req.getAddress());
        patient.setAllergyHistory(req.getAllergyHistory());
        patient.setPastHistory(req.getPastHistory());
        patientMapper.updateById(patient);
    }

    @Transactional
    public String addCard(Long patientId) {
        requirePatient(patientId);
        PatMedicalCard card = new PatMedicalCard();
        card.setPatientId(patientId);
        card.setCardNo(idGenerator.next("KP"));
        card.setCardType(1);
        card.setStatus(1);
        cardMapper.insert(card);
        return card.getCardNo();
    }

    private PatPatient requirePatient(Long id) {
        PatPatient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BizException(ErrorCode.A0001, "患者不存在");
        }
        return patient;
    }

    private PatientResponse toResponse(PatPatient patient) {
        PatientResponse resp = new PatientResponse();
        resp.setId(patient.getId());
        resp.setPatientNo(patient.getPatientNo());
        resp.setName(patient.getName());
        resp.setGender(patient.getGender());
        resp.setBirthDate(patient.getBirthDate());
        resp.setPhone(MaskUtil.maskPhone(patient.getPhone()));
        resp.setIdCardNo(MaskUtil.maskIdCard(cryptoUtil.decrypt(patient.getIdCardNo())));
        resp.setAddress(patient.getAddress());
        resp.setAllergyHistory(patient.getAllergyHistory());
        resp.setPastHistory(patient.getPastHistory());
        resp.setCreatedAt(patient.getCreatedAt());
        return resp;
    }
}
