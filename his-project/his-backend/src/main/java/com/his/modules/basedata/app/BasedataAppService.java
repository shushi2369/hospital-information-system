package com.his.modules.basedata.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.his.modules.basedata.entity.ChargeItem;
import com.his.modules.basedata.entity.Department;
import com.his.modules.basedata.entity.Doctor;
import com.his.modules.basedata.entity.Drug;
import com.his.modules.basedata.mapper.ChargeItemMapper;
import com.his.modules.basedata.mapper.DepartmentMapper;
import com.his.modules.basedata.mapper.DoctorMapper;
import com.his.modules.basedata.mapper.DrugMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 基础资料跨模块应用服务（模块边界：其他模块只允许经由本服务读取 bas_ 表，
 * 禁止直接读写 bas_ 表的 Mapper——《01 总体架构设计》§4.1）。
 */
@Service
@RequiredArgsConstructor
public class BasedataAppService {
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final DrugMapper drugMapper;
    private final ChargeItemMapper chargeItemMapper;

    public DoctorDTO getDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        return doctor == null ? null : toDoctorDTO(doctor);
    }

    public DoctorDTO getDoctorByUserId(Long userId) {
        Doctor doctor = doctorMapper.selectOne(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getUserId, userId).last("LIMIT 1"));
        return doctor == null ? null : toDoctorDTO(doctor);
    }

    /**
     * 行锁读取医生（挂号号源校验《05》R2：事务内锁定医生行后计数，防并发超发）
     */
    public DoctorDTO lockDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectByIdForUpdate(doctorId);
        return doctor == null ? null : toDoctorDTO(doctor);
    }

    public DepartmentDTO getDepartment(Long deptId) {
        Department dept = departmentMapper.selectById(deptId);
        if (dept == null) {
            return null;
        }
        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(dept.getId());
        dto.setDeptCode(dept.getDeptCode());
        dto.setDeptName(dept.getDeptName());
        dto.setDeptType(dept.getDeptType());
        dto.setLocation(dept.getLocation());
        dto.setStatus(dept.getStatus());
        return dto;
    }

    public List<DepartmentDTO> listDepartmentsByType(Integer deptType) {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .eq(deptType != null, Department::getDeptType, deptType)
                        .orderByAsc(Department::getId))
                .stream().map(d -> {
                    DepartmentDTO dto = new DepartmentDTO();
                    dto.setId(d.getId());
                    dto.setDeptCode(d.getDeptCode());
                    dto.setDeptName(d.getDeptName());
                    dto.setDeptType(d.getDeptType());
                    dto.setLocation(d.getLocation());
                    dto.setStatus(d.getStatus());
                    return dto;
                }).toList();
    }

    /** 医生字典 Map（列表批量解析姓名/费用用，避免逐行查询） */
    public Map<Long, DoctorDTO> doctorMap() {
        Map<Long, DoctorDTO> map = new java.util.HashMap<>();
        for (Doctor doctor : doctorMapper.selectList(null)) {
            map.put(doctor.getId(), toDoctorDTO(doctor));
        }
        return map;
    }

    /** 科室字典 Map */
    public Map<Long, DepartmentDTO> departmentMap() {
        Map<Long, DepartmentDTO> map = new java.util.HashMap<>();
        for (Department dept : departmentMapper.selectList(null)) {
            DepartmentDTO dto = new DepartmentDTO();
            dto.setId(dept.getId());
            dto.setDeptCode(dept.getDeptCode());
            dto.setDeptName(dept.getDeptName());
            dto.setDeptType(dept.getDeptType());
            dto.setLocation(dept.getLocation());
            dto.setStatus(dept.getStatus());
            map.put(dept.getId(), dto);
        }
        return map;
    }

    /** 挂号/收费模块取药品快照；不存在或停用返回 null，由调用方决定报错口径 */
    public DrugDTO getDrug(Long drugId) {
        Drug drug = drugMapper.selectById(drugId);
        if (drug == null) {
            return null;
        }
        return toDrugDTO(drug);
    }

    /** 全量启用药品（库存预警计算用） */
    public List<DrugDTO> listAllDrugs() {
        return drugMapper.selectList(new LambdaQueryWrapper<Drug>()
                        .eq(Drug::getStatus, 1).orderByAsc(Drug::getId))
                .stream().map(this::toDrugDTO).toList();
    }

    private DrugDTO toDrugDTO(Drug drug) {
        DrugDTO dto = new DrugDTO();
        dto.setId(drug.getId());
        dto.setDrugCode(drug.getDrugCode());
        dto.setDrugName(drug.getDrugName());
        dto.setSpec(drug.getSpec());
        dto.setUnit(drug.getUnit());
        dto.setRetailPrice(drug.getRetailPrice());
        dto.setStockWarningQty(drug.getStockWarningQty());
        dto.setStatus(drug.getStatus());
        return dto;
    }

    public ChargeItemDTO getChargeItem(Long itemId) {
        ChargeItem item = chargeItemMapper.selectById(itemId);
        return item == null ? null : toChargeItemDTO(item);
    }

    /** 批量取收费项目（床位一览等批量场景，避免逐条 N+1） */
    public Map<Long, ChargeItemDTO> listChargeItemsByIds(java.util.Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        return chargeItemMapper.selectBatchIds(itemIds).stream()
                .collect(java.util.stream.Collectors.toMap(ChargeItem::getId, this::toChargeItemDTO));
    }

    /** 按编码取收费项目（诊查费取数口径，见 application.yml his.registration.*） */
    public ChargeItemDTO getChargeItemByCode(String itemCode) {
        ChargeItem item = chargeItemMapper.selectOne(new LambdaQueryWrapper<ChargeItem>()
                .eq(ChargeItem::getItemCode, itemCode).last("LIMIT 1"));
        return item == null ? null : toChargeItemDTO(item);
    }

    private DoctorDTO toDoctorDTO(Doctor doctor) {
        DoctorDTO dto = new DoctorDTO();
        dto.setId(doctor.getId());
        dto.setUserId(doctor.getUserId());
        dto.setDeptId(doctor.getDeptId());
        Department dept = departmentMapper.selectById(doctor.getDeptId());
        dto.setDeptName(dept == null ? null : dept.getDeptName());
        dto.setDoctorCode(doctor.getDoctorCode());
        dto.setDoctorName(doctor.getDoctorName());
        dto.setTitle(doctor.getTitle());
        dto.setIsExpert(doctor.getIsExpert());
        dto.setNormalFee(doctor.getNormalFee());
        dto.setExpertFee(doctor.getExpertFee());
        dto.setDailyQuota(doctor.getDailyQuota());
        dto.setStatus(doctor.getStatus());
        return dto;
    }

    private ChargeItemDTO toChargeItemDTO(ChargeItem item) {
        ChargeItemDTO dto = new ChargeItemDTO();
        dto.setId(item.getId());
        dto.setItemCode(item.getItemCode());
        dto.setItemName(item.getItemName());
        dto.setCategory(item.getCategory());
        dto.setPrice(item.getPrice());
        dto.setUnit(item.getUnit());
        dto.setStatus(item.getStatus());
        return dto;
    }
}
