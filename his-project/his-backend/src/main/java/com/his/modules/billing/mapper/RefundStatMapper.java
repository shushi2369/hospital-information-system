package com.his.modules.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 退费统计查询（报表模块只读数据源）。
 */
@Mapper
public interface RefundStatMapper {

    @Select("SELECT DATE(refund_time) AS `date`, SUM(refund_amount) AS amount FROM bil_refund_bill "
            + "WHERE refund_time BETWEEN #{s} AND #{e} GROUP BY DATE(refund_time)")
    List<Map<String, Object>> dailyRefund(LocalDateTime s, LocalDateTime e);

    @Select("SELECT refund_time AS refundTime, refund_no AS refundNo, patient_id AS patientId, "
            + "reason, refund_amount AS refundAmount FROM bil_refund_bill "
            + "WHERE refund_time BETWEEN #{s} AND #{e} ORDER BY refund_time DESC")
    List<Map<String, Object>> refundRows(LocalDateTime s, LocalDateTime e);
}
