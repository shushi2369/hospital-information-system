package com.his.modules.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.clinic.app.VisitStatDTO;
import com.his.modules.clinic.entity.CliVisit;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface CliVisitMapper extends BaseMapper<CliVisit> {

    @Select("SELECT * FROM cli_visit WHERE registration_id = #{registrationId} LIMIT 1")
    CliVisit selectByRegistrationId(Long registrationId);

    /** 日门诊量（T-02，按就诊完成时间统计） */
    @Select("SELECT DATE(end_time) AS `date`, COUNT(*) AS count FROM cli_visit "
            + "WHERE status = 30 AND end_time >= #{start} AND end_time < DATE_ADD(#{end}, INTERVAL 1 DAY) "
            + "GROUP BY DATE(end_time) ORDER BY `date`")
    List<VisitStatDTO> dailyVisits(LocalDate start, LocalDate end);
}
