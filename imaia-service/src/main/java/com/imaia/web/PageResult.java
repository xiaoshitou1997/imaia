package com.imaia.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 统一分页包络：page 从 1 开始，size 上限 100。
 * 列表接口统一返回 items/page/size/total/totalPages，前端用同一个分页条。
 */
public record PageResult<T>(List<T> items, int page, int size, long total, int totalPages) {

    public static <T> PageResult<T> of(Page<T> result) {
        return new PageResult<>(result.getContent(), result.getNumber() + 1, result.getSize(),
                result.getTotalElements(), Math.max(1, result.getTotalPages()));
    }

    public static Pageable pageable(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return PageRequest.of(safePage - 1, safeSize);
    }
}
