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
        long num = Math.max(pageNum, 1);
        return new Page<>(num, size);
    }
}
