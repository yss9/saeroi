package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.ProcessType;
import java.util.List;

public record ProcessorSearchResponse(List<Item> items, long total, int page, int size) {
    public record Item(Long processorId, String name, String region,
            long minQuantity, long maxQuantity, List<EstimatedLeadTime> estimatedLeadTimes) {}

    public record EstimatedLeadTime(ProcessType processType, int leadDays) {}
}
