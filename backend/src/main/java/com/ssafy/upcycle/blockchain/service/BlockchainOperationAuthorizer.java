package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.dto.BlockchainOperationCreateRequest;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BlockchainOperationAuthorizer {
    private final MaterialRepository materials;
    private final MaterialListingRepository listings;
    private final MaterialTradeRepository trades;
    private final ProcessingJobRepository processingJobs;
    private final ProductionBatchRepository productionBatches;

    void requireReferenceAccess(Long userId, BlockchainOperationCreateRequest request) {
        boolean allowed = switch (request.referenceType()) {
            case MATERIAL -> materials.findById(request.referenceId())
                    .map(value -> userId.equals(value.getRegistrantId())).orElse(false);
            case TRADE -> isListingFunction(request.functionName())
                    ? listings.findById(request.referenceId()).map(value -> userId.equals(value.getSellerId())).orElse(false)
                    : trades.findById(request.referenceId()).map(value -> userId.equals(value.getSellerId()) || userId.equals(value.getBuyerId())).orElse(false);
            case PROCESSING -> processingJobs.findById(request.referenceId())
                    .map(value -> userId.equals(value.getBrandId()) || userId.equals(value.getProcessorId())).orElse(false);
            case PRODUCTION -> productionBatches.findById(request.referenceId())
                    .map(value -> userId.equals(value.getProducerId())).orElse(false);
        };
        if (!allowed) throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
    }

    private boolean isListingFunction(String functionName) {
        return "createListing".equals(functionName) || "cancelListing".equals(functionName);
    }
}
