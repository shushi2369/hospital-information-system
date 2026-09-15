package com.his.modules.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.billing.entity.BilChargeBill;
import org.apache.ibatis.annotations.Select;

public interface BilChargeBillMapper extends BaseMapper<BilChargeBill> {

    /** 行锁读取（退费事务串行化同一账单的并发退费，防止乐观锁静默丢写） */
    @Select("SELECT * FROM bil_charge_bill WHERE id = #{id} FOR UPDATE")
    BilChargeBill selectByIdForUpdate(Long id);
}
