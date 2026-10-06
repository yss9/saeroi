package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.dto.*;
import com.ssafy.upcycle.blockchain.entity.*;
import com.ssafy.upcycle.blockchain.repository.BlockchainOperationRepository;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockchainOperationService {
    private final BlockchainOperationRepository repository;
    private final UserWalletRepository wallets;
    private final BlockchainProperties properties;
    private final BlockchainOperationAuthorizer authorizer;
    private final Clock clock;

    @Transactional
    public BlockchainOperationResponse create(Long userId, BlockchainOperationCreateRequest request) {
        if (request.referenceType() != referenceTypeOf(request.contract())) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
        }
        final String selector;
        try { selector = BlockchainOperationCatalog.selector(request.contract(), request.functionName()); }
        catch (IllegalArgumentException invalid) { throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID); }
        authorizer.requireReferenceAccess(userId, request);
        String address = addressOf(request.contract());
        if (properties.chainId() == null || !validAddress(address)) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
        String wallet = wallets.findByUserIdAndActiveTrue(userId).map(UserWallet::getWalletAddress)
                .orElseThrow(() -> new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID));
        String operationId = "0x" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        BlockchainOperation operation = BlockchainOperation.create(operationId, userId,
                properties.chainId(), request.contract(), address, request.functionName(), selector,
                request.referenceType(), request.referenceId(), wallet, LocalDateTime.now(clock));
        return BlockchainOperationResponse.from(repository.save(operation));
    }

    @Transactional
    public BlockchainOperationResponse submit(Long userId, String operationId, BlockchainOperationSubmitRequest request) {
        BlockchainOperation operation = mine(userId, operationId);
        repository.findByTxHash(request.txHash().toLowerCase())
                .filter(found -> !found.getOperationId().equals(operation.getOperationId()))
                .ifPresent(found -> { throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID); });
        try { operation.submitted(request.txHash(), LocalDateTime.now(clock)); }
        catch (IllegalStateException invalid) { throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID); }
        return BlockchainOperationResponse.from(operation);
    }

    @Transactional(readOnly = true)
    public BlockchainOperationResponse find(Long userId, String operationId) {
        return BlockchainOperationResponse.from(mine(userId, operationId));
    }

    private BlockchainOperation mine(Long userId, String operationId) {
        return repository.findByOperationIdAndUserId(operationId.toLowerCase(), userId)
                .orElseThrow(() -> new BusinessException(BlockchainErrorCode.BLOCKCHAIN_TRANSACTION_NOT_FOUND));
    }
    public String addressOf(BlockchainContract contract) {
        return switch (contract) {
            case MATERIAL_TOKEN -> properties.materialTokenAddress();
            case MARKETPLACE -> properties.marketplaceAddress();
            case PROCESSING_REGISTRY -> properties.processingRegistryAddress();
            case PRODUCTION_REGISTRY -> properties.productionRegistryAddress();
        };
    }
    private BlockchainReferenceType referenceTypeOf(BlockchainContract contract) {
        return switch (contract) {
            case MATERIAL_TOKEN -> BlockchainReferenceType.MATERIAL;
            case MARKETPLACE -> BlockchainReferenceType.TRADE;
            case PROCESSING_REGISTRY -> BlockchainReferenceType.PROCESSING;
            case PRODUCTION_REGISTRY -> BlockchainReferenceType.PRODUCTION;
        };
    }
    private boolean validAddress(String value) { return value != null && value.matches("^0x[0-9a-fA-F]{40}$"); }
}
