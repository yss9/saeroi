package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import java.util.List;

public record ProcessingChainPreparationResponse(
        Long jobId,
        Long chainJobId,
        ProcessingJobStatus status,
        String brandWallet,
        String processorWallet,
        Long inputTokenId,
        long inputQuantity,
        List<ProcessType> processTypes) {}
