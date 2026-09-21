package com.ssafy.upcycle.prediction.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.prediction.PredictionErrorCode;
import com.ssafy.upcycle.prediction.config.PredictionProperties;
import com.ssafy.upcycle.prediction.dto.*;
import com.ssafy.upcycle.prediction.entity.*;
import com.ssafy.upcycle.prediction.repository.*;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.CompanyRoleApprovalStatus;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PredictionMarketService {
    private final PredictionMarketRepository markets;
    private final PredictionBucketRepository buckets;
    private final PredictionResponseRepository responses;
    private final PredictionPointGrantRepository grants;
    private final ProductionBatchRepository productionBatches;
    private final ProductModelRepository productModels;
    private final UserRepository users;
    private final CompanyProfileRepository companyProfiles;
    private final UserWalletRepository wallets;
    private final PredictionChainGateway chain;
    private final PredictionProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PredictionMarketEligibleBatchResponse> eligibleBatches() {
        return productionBatches
                .findAllByStatusOrderByConfirmedAtDesc(ProductionBatchStatus.CONFIRMED)
                .stream()
                .filter(batch -> batch.getChainBatchId() != null && batch.getProductModelId() != null)
                .filter(batch -> !markets.existsByProductionBatchId(batch.getId()))
                .filter(batch -> companyProfiles.findById(batch.getProducerId())
                        .map(profile -> profile.getRoleApprovalStatus() == CompanyRoleApprovalStatus.APPROVED)
                        .orElse(false))
                .map(this::eligibleBatchResponse)
                .toList();
    }

    @Transactional
    public PredictionMarketResponse create(PredictionMarketCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        ProductionBatch batch = productionBatches.findById(request.productionBatchId())
                .orElseThrow(() -> error(PredictionErrorCode.INVALID_MARKET));
        boolean approvedBrand = companyProfiles.findById(batch.getProducerId())
                .map(profile -> profile.getRoleApprovalStatus() == CompanyRoleApprovalStatus.APPROVED)
                .orElse(false);
        if (!batch.isConfirmed() || batch.getChainBatchId() == null || batch.getProductModelId() == null
                || markets.existsByProductionBatchId(batch.getId()) || !approvedBrand) {
            throw error(PredictionErrorCode.INVALID_MARKET);
        }
        ProductModel model = productModels.findById(batch.getProductModelId())
                .orElseThrow(() -> error(PredictionErrorCode.INVALID_MARKET));
        List<Integer> bounds = List.copyOf(request.bucketUpperBounds());
        validateSchedule(now, request);
        validateBounds(bounds, batch.getProducedQuantity());

        PredictionChainGateway.MarketCreated created = chain.createMarket(
                batch.getChainBatchId(), model.getId(), model.getPrice(), request.bettingEndsAt(),
                request.salesStartsAt(), request.salesEndsAt(), bounds);
        PredictionMarket market = markets.save(PredictionMarket.create(
                created.chainMarketId(), batch.getId(), batch.getChainBatchId(), model.getId(),
                model.getName(), model.getThumbnailUrl(), batch.getProducerId(), batch.getProducerWallet(),
                model.getPrice(), batch.getProducedQuantity(), request.bettingEndsAt(),
                request.salesStartsAt(), request.salesEndsAt(), created.transactionHash()));
        int lower = 0;
        for (int index = 0; index < bounds.size(); index++) {
            int upper = bounds.get(index);
            buckets.save(PredictionBucket.of(market.getId(), index, lower, upper));
            lower = upper + 1;
        }
        return response(market);
    }

    @Transactional(readOnly = true)
    public List<PredictionMarketResponse> list() {
        return markets.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public PredictionMarketResponse detail(Long marketId) { return response(requireMarket(marketId)); }

    @Transactional
    public PredictionSurveyResponse recordSurvey(Long userId, Long marketId, PredictionSurveyRequest request) {
        UserWallet wallet = requireConsumerWallet(userId);
        PredictionMarket market = requireMarket(marketId);
        if (market.getStatus() != PredictionMarketStatus.OPEN
                || !LocalDateTime.now(clock).isBefore(market.getBettingEndsAt())) {
            throw error(PredictionErrorCode.INVALID_MARKET);
        }
        if (responses.existsByMarketIdAndUserId(marketId, userId)) throw error(PredictionErrorCode.RESPONSE_DUPLICATED);
        boolean validBucket = buckets.findAllByMarketIdOrderByBucketIndex(marketId).stream()
                .anyMatch(bucket -> bucket.getBucketIndex() == request.bucketIndex());
        BigInteger amount;
        try { amount = new BigInteger(request.stakeAmount()); }
        catch (NumberFormatException exception) { throw error(PredictionErrorCode.INVALID_MARKET); }
        if (!validBucket || amount.signum() <= 0 || !chain.verifyBet(
                request.transactionHash(), market.getChainMarketId(), wallet.getWalletAddress(),
                request.bucketIndex(), amount)) throw error(PredictionErrorCode.CHAIN_TRANSACTION_FAILED);
        return PredictionSurveyResponse.from(responses.save(PredictionResponse.of(
                marketId, userId, wallet.getWalletAddress(), request.bucketIndex(), amount.toString(),
                request.reasonCode(), request.reasonText(), request.transactionHash())));
    }

    @Transactional
    public PredictionPointGrantResponse grantWelcomePoint(Long userId) {
        UserWallet wallet = requireConsumerWallet(userId);
        if (grants.existsById(userId)) throw error(PredictionErrorCode.POINT_ALREADY_GRANTED);
        BigInteger amount = properties.effectiveWelcomePointAmount();
        String txHash = chain.mintPoint(wallet.getWalletAddress(), amount);
        return PredictionPointGrantResponse.from(grants.save(PredictionPointGrant.of(
                userId, wallet.getWalletAddress(), amount.toString(), txHash)));
    }

    private UserWallet requireConsumerWallet(Long userId) {
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> error(PredictionErrorCode.PARTICIPATION_FORBIDDEN));
        if (user.getRole() != UserRole.CONSUMER) throw error(PredictionErrorCode.PARTICIPATION_FORBIDDEN);
        return wallets.findByUserIdAndActiveTrue(userId).filter(UserWallet::isVerified)
                .orElseThrow(() -> error(PredictionErrorCode.WALLET_REQUIRED));
    }

    /**
     * 예측시장을 무효화한다. 참여자의 스테이크는 컨트랙트가 환불 대상으로 돌린다.
     *
     * <p><b>온체인이 먼저다.</b> 체인 호출이 실패하면 예외가 트랜잭션을 되돌려 DB 는 OPEN 그대로
     * 남는다. 순서를 뒤집으면 체인에서는 아직 열려 있는데 화면만 무효로 보이는 상태가 생기고,
     * 그 사이 들어온 베팅은 어디에도 기록되지 않는다.
     *
     * <p>오라클 워커가 같은 행을 집어 결과를 확정하는 것과 겹치지 않도록 잠그고 읽는다.
     */
    @Transactional
    public PredictionMarketResponse cancel(Long marketId) {
        PredictionMarket market = markets.findWithLockById(marketId)
                .orElseThrow(() -> error(PredictionErrorCode.MARKET_NOT_FOUND));
        if (market.getStatus() != PredictionMarketStatus.OPEN) {
            throw error(PredictionErrorCode.MARKET_NOT_CANCELABLE);
        }
        market.cancel(chain.cancelMarket(market.getChainMarketId()), LocalDateTime.now(clock));
        return response(market);
    }

    private PredictionMarket requireMarket(Long id) {
        return markets.findById(id).orElseThrow(() -> error(PredictionErrorCode.MARKET_NOT_FOUND));
    }

    private PredictionMarketResponse response(PredictionMarket market) {
        List<PredictionBucketResponse> values = buckets.findAllByMarketIdOrderByBucketIndex(market.getId()).stream()
                .map(value -> new PredictionBucketResponse(value.getBucketIndex(), value.getLowerBound(), value.getUpperBound()))
                .toList();
        return PredictionMarketResponse.from(market, values);
    }

    private PredictionMarketEligibleBatchResponse eligibleBatchResponse(ProductionBatch batch) {
        ProductModel model = productModels.findById(batch.getProductModelId())
                .orElseThrow(() -> error(PredictionErrorCode.INVALID_MARKET));
        CompanyProfile profile = companyProfiles.findById(batch.getProducerId())
                .orElseThrow(() -> error(PredictionErrorCode.INVALID_MARKET));
        return new PredictionMarketEligibleBatchResponse(
                batch.getId(),
                batch.getChainBatchId(),
                batch.getBatchCode(),
                batch.getBatchName(),
                profile.getCompanyName(),
                model.getName(),
                batch.getProducedQuantity());
    }

    private static void validateSchedule(LocalDateTime now, PredictionMarketCreateRequest request) {
        if (!now.isBefore(request.bettingEndsAt())
                || request.bettingEndsAt().isAfter(request.salesStartsAt())
                || !request.salesStartsAt().isBefore(request.salesEndsAt())) throw error(PredictionErrorCode.INVALID_MARKET);
    }

    private static void validateBounds(List<Integer> bounds, int productionQuantity) {
        int previous = -1;
        for (Integer bound : bounds) {
            if (bound == null || bound <= previous) throw error(PredictionErrorCode.INVALID_MARKET);
            previous = bound;
        }
        if (previous != productionQuantity) throw error(PredictionErrorCode.INVALID_MARKET);
    }

    private static BusinessException error(PredictionErrorCode code) { return new BusinessException(code); }
}

