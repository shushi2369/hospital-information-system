package com.his.modules.patient.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.common.BizException;
import com.his.common.ErrorCode;
import com.his.modules.patient.entity.PatMedicalCard;
import com.his.modules.patient.entity.PatPatient;
import com.his.modules.patient.mapper.PatMedicalCardMapper;
import com.his.modules.patient.mapper.PatPatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 患者中心跨模块应用服务（挂号/医生工作站/收费模块统一经此读取 pat_ 表）。
 */
@Service
@RequiredArgsConstructor
public class PatientAppService {
    private final PatPatientMapper patientMapper;
    private final PatMedicalCardMapper cardMapper;

    public PatientDTO getById(Long patientId) {
        PatPatient patient = patientMapper.selectById(patientId);
        return patient == null ? null : toDTO(patient);
    }

    /** 患者不存在或停用抛 A0001 */
    public PatientDTO requireActive(Long patientId) {
        PatPatient patient = patientMapper.selectById(patientId);
        if (patient == null || patient.getStatus() == 0) {
            throw new BizException(ErrorCode.A0001, "患者不存在或已停用");
        }
        return toDTO(patient);
    }

    public List<PatientDTO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return patientMapper.selectBatchIds(ids).stream().map(this::toDTO).toList();
    }

    /** 患者最新有效就诊卡号（无卡返回 null） */
    public String latestCardNo(Long patientId) {
        PatMedicalCard card = cardMapper.selectOne(new LambdaQueryWrapper<PatMedicalCard>()
                .eq(PatMedicalCard::getPatientId, patientId)
                .eq(PatMedicalCard::getStatus, 1)
                .orderByDesc(PatMedicalCard::getId)
                .last("LIMIT 1"));
        return card == null ? null : card.getCardNo();
    }

    private PatientDTO toDTO(PatPatient patient) {
        PatientDTO dto = new PatientDTO();
        dto.setId(patient.getId());
        dto.setPatientNo(patient.getPatientNo());
        dto.setName(patient.getName());
        dto.setGender(patient.getGender());
        dto.setBirthDate(patient.getBirthDate());
        dto.setPhone(patient.getPhone());
        dto.setAllergyHistory(patient.getAllergyHistory());
        dto.setStatus(patient.getStatus());
        return dto;
    }
}
