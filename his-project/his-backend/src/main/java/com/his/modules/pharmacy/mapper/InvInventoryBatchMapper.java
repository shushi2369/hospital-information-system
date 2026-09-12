package com.his.modules.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.pharmacy.entity.InvInventoryBatch;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InvInventoryBatchMapper extends BaseMapper<InvInventoryBatch> {

    /** 行锁读取批次（出入库事务防并发） */
    @Select("SELECT * FROM inv_inventory_batch WHERE id = #{id} FOR UPDATE")
    InvInventoryBatch selectByIdForUpdate(Long id);

    /** 可拣批次：可用、未过期、有库存，FEFO（效期早先出）排序 */
    @Select("SELECT * FROM inv_inventory_batch WHERE drug_id = #{drugId} AND status = 1 "
            + "AND expiry_date > CURDATE() AND quantity > 0 ORDER BY expiry_date ASC, id ASC")
    List<InvInventoryBatch> selectPickable(Long drugId);
}
