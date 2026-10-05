package com.his.modules.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.registration.app.DailyStatDTO;
import com.his.modules.registration.entity.RegRegistration;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface RegRegistrationMapper extends BaseMapper<RegRegistration> {

    /** 当日该医生时段已分配最大排队号（含退号号段不复用，《05》R3） */
    @Select("SELECT MAX(queue_no) FROM reg_registration "
            + "WHERE doctor_id = #{doctorId} AND reg_date = #{regDate} AND period = #{period}")
    Integer selectMaxQueueNo(Long doctorId, Object regDate, Integer period);

    /** 有效挂号数（防重复挂号 R1 用） */
    @Select("SELECT COUNT(*) FROM reg_registration "
            + "WHERE patient_id = #{patientId} AND doctor_id = #{doctorId} "
            + "AND reg_date = #{regDate} AND period = #{period} AND status = 10")
    Long countValidByPatient(Long patientId, Long doctorId, Object regDate, Integer period);

    /** 号源占用数（已挂号+已就诊占号，退号/过号释放，《05》R2） */
    @Select("SELECT COUNT(*) FROM reg_registration "
            + "WHERE doctor_id = #{doctorId} AND reg_date = #{regDate} AND period = #{period} "
            + "AND status IN (10, 30)")
    Long countOccupied(Long doctorId, Object regDate, Integer period);

    /** 日挂号量（T-01，不含已退号） */
    @Select("SELECT reg_date AS `date`, COUNT(*) AS count FROM reg_registration "
            + "WHERE reg_date BETWEEN #{start} AND #{end} AND status <> 20 "
            + "GROUP BY reg_date ORDER BY reg_date")
    List<DailyStatDTO> dailyRegistrations(LocalDate start, LocalDate end);

    /** 科室挂号排名（T-01 下钻） */
    @Select("SELECT d.dept_name AS `name`, COUNT(*) AS count FROM reg_registration r "
            + "JOIN bas_department d ON r.dept_id = d.id "
            + "WHERE r.reg_date BETWEEN #{start} AND #{end} AND r.status <> 20 "
            + "GROUP BY d.id, d.dept_name ORDER BY count DESC")
    List<DailyStatDTO> deptRegistrationRanking(LocalDate start, LocalDate end);
}
