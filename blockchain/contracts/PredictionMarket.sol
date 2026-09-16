// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {Math} from "@openzeppelin/contracts/utils/math/Math.sol";

interface IProductionRegistryForPrediction {
    struct Batch {
        address brand;
        uint256 productQuantity;
        bytes32 evidenceHash;
        uint8 status;
        uint64 createdAt;
        uint64 confirmedAt;
        uint16 upcycledRatioBps;
    }

    function getBatch(uint256 batchId) external view returns (Batch memory);
}

/// @title PredictionMarket
/// @notice 확정된 생산 배치의 기간 내 판매량을 구간으로 예측하고 parimutuel 방식으로 정산한다.
contract PredictionMarket is AccessControl, ReentrancyGuard {
    using SafeERC20 for IERC20;

    bytes32 public constant MARKET_CREATOR_ROLE = keccak256("MARKET_CREATOR_ROLE");
    bytes32 public constant ORACLE_ROLE = keccak256("ORACLE_ROLE");

    uint8 private constant PRODUCTION_STATUS_CONFIRMED = 3;

    enum MarketStatus { NONE, OPEN, RESOLVED, CANCELED }

    struct Market {
        uint256 batchId;
        uint256 productModelId;
        uint256 unitPrice;
        uint256 productionQuantity;
        address brand;
        uint64 bettingEndsAt;
        uint64 salesStartAt;
        uint64 salesEndAt;
        MarketStatus status;
        uint32 winningBucket;
        uint256 soldQuantity;
        bytes32 resultHash;
        uint256 totalPool;
        uint256 remainingPayout;
        uint256 remainingWinningStake;
        bool refundOnly;
    }

    IERC20 public immutable predictionToken;
    IProductionRegistryForPrediction public immutable productionRegistry;
    uint256 public nextMarketId = 1;

    mapping(uint256 => Market) private _markets;
    mapping(uint256 => uint256[]) private _bucketUpperBounds;
    mapping(uint256 => mapping(uint256 => uint256)) public bucketPool;
    mapping(uint256 => mapping(address => mapping(uint256 => uint256))) public userBucketStake;
    mapping(uint256 => mapping(address => uint256)) public userTotalStake;
    mapping(uint256 => mapping(address => bool)) public claimed;
    mapping(uint256 => uint256) public batchMarketId;

    event MarketCreated(
        uint256 indexed marketId,
        uint256 indexed batchId,
        uint256 indexed productModelId,
        address brand,
        uint256 productionQuantity,
        uint256 unitPrice,
        uint64 bettingEndsAt,
        uint64 salesStartAt,
        uint64 salesEndAt
    );
    event BetPlaced(uint256 indexed marketId, address indexed bettor, uint256 indexed bucketIndex, uint256 amount);
    event MarketResolved(
        uint256 indexed marketId,
        uint256 soldQuantity,
        uint256 indexed winningBucket,
        bytes32 resultHash,
        bool refundOnly
    );
    event MarketCanceled(uint256 indexed marketId);
    event RewardClaimed(uint256 indexed marketId, address indexed bettor, uint256 amount);
    event RefundClaimed(uint256 indexed marketId, address indexed bettor, uint256 amount);

    constructor(address tokenAddress, address productionRegistryAddress, address admin) {
        require(tokenAddress != address(0), "PredictionMarket: zero token");
        require(productionRegistryAddress != address(0), "PredictionMarket: zero registry");
        require(admin != address(0), "PredictionMarket: zero admin");
        predictionToken = IERC20(tokenAddress);
        productionRegistry = IProductionRegistryForPrediction(productionRegistryAddress);
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(MARKET_CREATOR_ROLE, admin);
        _grantRole(ORACLE_ROLE, admin);
    }

    function createMarket(
        uint256 batchId,
        uint256 productModelId,
        uint256 unitPrice,
        uint64 bettingEndsAt,
        uint64 salesStartAt,
        uint64 salesEndAt,
        uint256[] calldata bucketUpperBounds
    ) external onlyRole(MARKET_CREATOR_ROLE) returns (uint256 marketId) {
        require(batchMarketId[batchId] == 0, "PredictionMarket: batch already used");
        require(productModelId > 0, "PredictionMarket: invalid product");
        require(unitPrice > 0, "PredictionMarket: invalid price");
        require(block.timestamp < bettingEndsAt, "PredictionMarket: invalid betting deadline");
        require(bettingEndsAt <= salesStartAt && salesStartAt < salesEndAt, "PredictionMarket: invalid sales period");

        IProductionRegistryForPrediction.Batch memory batch = productionRegistry.getBatch(batchId);
        require(batch.status == PRODUCTION_STATUS_CONFIRMED, "PredictionMarket: batch is not confirmed");
        require(batch.brand != address(0) && batch.productQuantity > 0, "PredictionMarket: invalid batch");
        _validateBuckets(bucketUpperBounds, batch.productQuantity);

        marketId = nextMarketId++;
        Market storage market = _markets[marketId];
        market.batchId = batchId;
        market.productModelId = productModelId;
        market.unitPrice = unitPrice;
        market.productionQuantity = batch.productQuantity;
        market.brand = batch.brand;
        market.bettingEndsAt = bettingEndsAt;
        market.salesStartAt = salesStartAt;
        market.salesEndAt = salesEndAt;
        market.status = MarketStatus.OPEN;
        _bucketUpperBounds[marketId] = bucketUpperBounds;
        batchMarketId[batchId] = marketId;

        emit MarketCreated(
            marketId,
            batchId,
            productModelId,
            batch.brand,
            batch.productQuantity,
            unitPrice,
            bettingEndsAt,
            salesStartAt,
            salesEndAt
        );
    }

    function placeBet(uint256 marketId, uint256 bucketIndex, uint256 amount) external nonReentrant {
        Market storage market = _market(marketId);
        require(market.status == MarketStatus.OPEN, "PredictionMarket: market is not open");
        require(block.timestamp < market.bettingEndsAt, "PredictionMarket: betting is closed");
        require(bucketIndex < _bucketUpperBounds[marketId].length, "PredictionMarket: invalid bucket");
        require(amount > 0, "PredictionMarket: amount is zero");
        require(msg.sender != market.brand, "PredictionMarket: brand cannot bet");
        require(!hasRole(DEFAULT_ADMIN_ROLE, msg.sender), "PredictionMarket: admin cannot bet");
        require(!hasRole(ORACLE_ROLE, msg.sender), "PredictionMarket: oracle cannot bet");

        userBucketStake[marketId][msg.sender][bucketIndex] += amount;
        userTotalStake[marketId][msg.sender] += amount;
        bucketPool[marketId][bucketIndex] += amount;
        market.totalPool += amount;
        predictionToken.safeTransferFrom(msg.sender, address(this), amount);

        emit BetPlaced(marketId, msg.sender, bucketIndex, amount);
    }

    function submitResult(uint256 marketId, uint256 soldQuantity, bytes32 resultHash) external onlyRole(ORACLE_ROLE) {
        Market storage market = _market(marketId);
        require(market.status == MarketStatus.OPEN, "PredictionMarket: market is not open");
        require(block.timestamp > market.salesEndAt, "PredictionMarket: sales period is not over");
        require(soldQuantity <= market.productionQuantity, "PredictionMarket: sales exceed production");
        require(resultHash != bytes32(0), "PredictionMarket: result is empty");
        require(userTotalStake[marketId][msg.sender] == 0, "PredictionMarket: bettor cannot resolve");

        uint256 winningBucket = _findBucket(marketId, soldQuantity);
        uint256 winningStake = bucketPool[marketId][winningBucket];
        market.status = MarketStatus.RESOLVED;
        market.winningBucket = uint32(winningBucket);
        market.soldQuantity = soldQuantity;
        market.resultHash = resultHash;
        market.refundOnly = winningStake == 0;
        if (winningStake > 0) {
            market.remainingPayout = market.totalPool;
            market.remainingWinningStake = winningStake;
        }

        emit MarketResolved(marketId, soldQuantity, winningBucket, resultHash, market.refundOnly);
    }

    function cancelMarket(uint256 marketId) external onlyRole(MARKET_CREATOR_ROLE) {
        Market storage market = _market(marketId);
        require(market.status == MarketStatus.OPEN, "PredictionMarket: market is not open");
        require(market.totalPool == 0 || block.timestamp > market.salesEndAt, "PredictionMarket: active bets exist");
        market.status = MarketStatus.CANCELED;
        market.refundOnly = true;
        emit MarketCanceled(marketId);
    }

    function claim(uint256 marketId) external nonReentrant returns (uint256 amount) {
        Market storage market = _market(marketId);
        require(!claimed[marketId][msg.sender], "PredictionMarket: already claimed");

        if (market.status == MarketStatus.CANCELED || (market.status == MarketStatus.RESOLVED && market.refundOnly)) {
            amount = userTotalStake[marketId][msg.sender];
            require(amount > 0, "PredictionMarket: nothing to refund");
            claimed[marketId][msg.sender] = true;
            predictionToken.safeTransfer(msg.sender, amount);
            emit RefundClaimed(marketId, msg.sender, amount);
            return amount;
        }

        require(market.status == MarketStatus.RESOLVED, "PredictionMarket: result is not final");
        uint256 winningStake = userBucketStake[marketId][msg.sender][market.winningBucket];
        require(winningStake > 0, "PredictionMarket: no winning stake");
        claimed[marketId][msg.sender] = true;

        if (winningStake == market.remainingWinningStake) {
            amount = market.remainingPayout;
        } else {
            amount = Math.mulDiv(market.totalPool, winningStake, bucketPool[marketId][market.winningBucket]);
        }
        market.remainingWinningStake -= winningStake;
        market.remainingPayout -= amount;
        predictionToken.safeTransfer(msg.sender, amount);
        emit RewardClaimed(marketId, msg.sender, amount);
    }

    function getMarket(uint256 marketId) external view returns (Market memory) {
        return _markets[marketId];
    }

    function getBucketUpperBounds(uint256 marketId) external view returns (uint256[] memory) {
        return _bucketUpperBounds[marketId];
    }

    function bucketCount(uint256 marketId) external view returns (uint256) {
        return _bucketUpperBounds[marketId].length;
    }

    function previewBucket(uint256 marketId, uint256 soldQuantity) external view returns (uint256) {
        Market storage market = _market(marketId);
        require(soldQuantity <= market.productionQuantity, "PredictionMarket: sales exceed production");
        return _findBucket(marketId, soldQuantity);
    }

    function _validateBuckets(uint256[] calldata bounds, uint256 productionQuantity) private pure {
        require(bounds.length >= 2, "PredictionMarket: insufficient buckets");
        uint256 previous;
        for (uint256 i; i < bounds.length; ++i) {
            require(bounds[i] > previous, "PredictionMarket: buckets not increasing");
            previous = bounds[i];
        }
        require(previous == productionQuantity, "PredictionMarket: buckets must cover production");
    }

    function _findBucket(uint256 marketId, uint256 soldQuantity) private view returns (uint256) {
        uint256[] storage bounds = _bucketUpperBounds[marketId];
        for (uint256 i; i < bounds.length; ++i) {
            if (soldQuantity <= bounds[i]) return i;
        }
        revert("PredictionMarket: bucket not found");
    }

    function _market(uint256 marketId) private view returns (Market storage market) {
        market = _markets[marketId];
        require(market.status != MarketStatus.NONE, "PredictionMarket: market does not exist");
    }
}
