package com.his.modules.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 收费统计查询（报表模块只读数据源）。
 */
@Mapper
public interface ChargeStatMapper {

    @Select("SELECT DATE(pay_time) AS `date`, SUM(payable_amount) AS amount FROM bil_charge_bill "
            + "WHERE pay_time BETWEEN #{s} AND #{e} GROUP BY DATE(pay_time)")
    List<Map<String, Object>> dailyCharge(LocalDateTime s, LocalDateTime e);

    @Select("SELECT fee_type AS feeType, SUM(amount) AS amount FROM bil_charge_detail d "
            + "JOIN bil_charge_bill b ON d.bill_id = b.id "
            + "WHERE b.pay_time BETWEEN #{s} AND #{e} GROUP BY fee_type")
    List<Map<String, Object>> feeTypeDistribution(LocalDateTime s, LocalDateTime e);

    @Select("SELECT b.pay_time AS payTime, b.bill_no AS billNo, b.patient_id AS patientId, "
            + "d.item_name AS itemName, d.fee_type AS feeType, d.quantity, d.unit_price AS unitPrice, d.amount "
            + "FROM bil_charge_detail d JOIN bil_charge_bill b ON d.bill_id = b.id "
            + "WHERE b.pay_time BETWEEN #{s} AND #{e} ORDER BY b.pay_time DESC")
    List<Map<String, Object>> detailRows(LocalDateTime s, LocalDateTime e);
}
