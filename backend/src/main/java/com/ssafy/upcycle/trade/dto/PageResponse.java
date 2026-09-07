package com.ssafy.upcycle.trade.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지 응답 봉투.
 *
 * <p>Spring 의 {@code Page} 를 그대로 직렬화하지 않는다. {@code PageImpl} 의 JSON 모양은
 * 버전에 따라 바뀌고 (Boot 3.3 부터는 직렬화 시 경고를 낸다) {@code pageable}·{@code sort}
 * 같은 내부 구조가 그대로 노출되어 프론트가 서버의 구현에 묶인다. 화면이 실제로 쓰는
 * 네 가지만 고정한다.
 *
 * <p>{@code page} 는 0-based 다. 서버 기준을 그대로 쓴다 — 한쪽에서 1 을 더하면 다른 쪽에서
 * 빼야 하고, 그 규칙은 언젠가 반드시 한 군데서 빠진다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
