package com.his.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页请求：pageSize 上限 200（《03 接口设计》§1.2）
 */
@Getter
@Setter
public class PageQuery {
    private long pageNum = 1;
    private long pageSize = 20;

    public <T> Page<T> toPage() {
        long size = Math.min(Math.max(pageSize, 1), 200);
        // 一百二十六轮【11】P3：pageNum 上限 10000——深翻页 OFFSET 无界在大表上是慢查询面
        long num = Math.min(Math.max(pageNum, 1), 10_000);
        return new Page<>(num, size);
    }
}
