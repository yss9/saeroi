package com.ssafy.upcycle.processing.dto;

import java.util.List;

public record ProcessorSearchResponse(List<Item> items, long total, int page, int size) {
    public record Item(Long processorId, String name, String region,
            long minQuantity, long maxQuantity, Integer estimatedLeadDays) {}
}
