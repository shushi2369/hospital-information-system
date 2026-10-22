package com.his.modules.inp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.inp.entity.InpDailyFee;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface InpDailyFeeMapper extends BaseMapper<InpDailyFee> {

    /** 床位费条件插入：仅住院仍为在院/出院未结时落库，INSERT..SELECT 对住院行加共享锁，
     *  与出院 FOR UPDATE 串行，杜绝定时任务给已结算住院补记孤儿费用 */
    @Insert("INSERT INTO inp_daily_fee (admission_id, fee_date, fee_type, source_type, source_detail_id, "
            + "item_name, quantity, unit_price, amount, charge_status, status) "
            + "SELECT #{admissionId}, #{feeDate}, #{feeType}, 1, #{bedId}, #{itemName}, 1, #{price}, #{amount}, 0, 1 "
            + "FROM inp_admission WHERE id = #{admissionId} AND status IN (10, 20)")
    int insertBedFeeIfActive(@Param("admissionId") Long admissionId,
                             @Param("feeDate") LocalDate feeDate,
                             @Param("feeType") Integer feeType,
                             @Param("bedId") Long bedId,
                             @Param("itemName") String itemName,
                             @Param("price") BigDecimal price,
                             @Param("amount") BigDecimal amount);
}
