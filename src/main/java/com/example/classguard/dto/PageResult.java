package com.example.classguard.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页响应结构：{"total":总数,"page":当前页,"size":每页条数,"list":[...]}（REST 文档 §2）。
 * <p>分页约定：page ≥ 1（默认 1），size 默认 20、上限 100。
 */
@Data
public class PageResult<T> {

    /** 总数 */
    private long total;

    /** 当前页（≥1） */
    private int page;

    /** 每页条数（默认 20，上限 100） */
    private int size;

    /** 当前页数据 */
    private List<T> list;

    public static <T> PageResult<T> of(long total, int page, int size, List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.total = total;
        result.page = page;
        result.size = size;
        result.list = list;
        return result;
    }
}
