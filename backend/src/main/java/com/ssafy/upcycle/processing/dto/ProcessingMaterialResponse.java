package com.ssafy.upcycle.processing.dto;

public record ProcessingMaterialResponse(Long materialId, String materialCode, String name,
        String materialType, long quantity, long lockedQuantity, long availableQuantity) {}
