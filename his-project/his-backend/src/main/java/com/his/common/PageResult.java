package com.his.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.function.Function;

@Getter
@Setter
public class PageResult<T> {
    private long total;
    private List<T> list;

    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> r = new PageResult<>();
        r.total = page.getTotal();
        r.list = page.getRecords();
        return r;
    }

    public static <S, T> PageResult<T> of(Page<S> page, Function<S, T> mapper) {
        PageResult<T> r = new PageResult<>();
        r.total = page.getTotal();
        r.list = page.getRecords().stream().map(mapper).toList();
        return r;
    }
}
