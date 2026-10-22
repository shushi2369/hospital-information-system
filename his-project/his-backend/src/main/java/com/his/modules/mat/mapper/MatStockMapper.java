package com.his.modules.mat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.his.modules.mat.entity.MatStock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MatStockMapper extends BaseMapper<MatStock> {

    /** 原子扣减可用库存：守卫在更新时重算过期批次，防两次领用各自通过预检后把过期库存也领走 */
    @Update("UPDATE mat_stock SET quantity = quantity - #{qty} "
            + "WHERE material_id = #{materialId} "
            + "AND quantity - (SELECT COALESCE(SUM(b.quantity), 0) FROM mat_batch b "
            + "WHERE b.material_id = #{materialId} AND b.expire_date <= CURDATE() AND b.quantity > 0) >= #{qty}")
    int deductAvailable(@Param("materialId") Long materialId, @Param("qty") int qty);
}
