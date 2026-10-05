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

    /** 收入明细分页：UNION ALL 合并收费明细行与退费单行，SQL 侧 LIMIT/OFFSET 真分页
     *  （内存假分页会先把区间全表载入堆，长区间导出可 OOM） */
    // tieKey：pay_time 截到秒后同秒多行是常态，缺唯一排序键会让 LIMIT/OFFSET 翻页重行/漏行（八十七轮 SQL 审计）
    @Select("SELECT t.* FROM ("
            + "SELECT b.pay_time AS payTime, b.bill_no AS billNo, b.patient_id AS patientId, "
            + "d.item_name AS itemName, d.fee_type AS feeType, d.quantity, d.unit_price AS unitPrice, d.amount, NULL AS reason, b.id AS tieKey "
            + "FROM bil_charge_detail d JOIN bil_charge_bill b ON d.bill_id = b.id "
            + "WHERE b.pay_time BETWEEN #{s} AND #{e} "
            + "UNION ALL "
            + "SELECT r.refund_time, r.refund_no, r.patient_id, CONCAT('退费：', r.reason), "
            + "NULL, NULL, NULL, r.refund_amount, r.reason, r.id AS tieKey "
            + "FROM bil_refund_bill r WHERE r.refund_time BETWEEN #{s} AND #{e}"
            + ") t ORDER BY t.payTime DESC, t.tieKey DESC LIMIT #{offset}, #{limit}")
    List<Map<String, Object>> detailRowsPage(LocalDateTime s, LocalDateTime e, long offset, long limit);

    @Select("SELECT (SELECT COUNT(*) FROM bil_charge_detail d JOIN bil_charge_bill b ON d.bill_id = b.id "
            + "WHERE b.pay_time BETWEEN #{s} AND #{e}) "
            + "+ (SELECT COUNT(*) FROM bil_refund_bill r WHERE r.refund_time BETWEEN #{s} AND #{e})")
    long detailRowsCount(LocalDateTime s, LocalDateTime e);
}
