// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

interface IProductionMaterialToken {
    function availableBalance(address owner, uint256 tokenId) external view returns (uint256);
    function rootOriginOf(uint256 tokenId) external view returns (uint8);
    function createMaterialLock(address owner, uint256 tokenId, uint256 amount, uint8 purpose, uint256 referenceId) external returns (uint256);
    function consumeLockedMaterial(uint256 lockId, uint256 referenceId, uint8 purpose, uint8 reason) external returns (uint256, uint256);
    function mintDerivedMaterial(address to, uint256 amount, uint8 rootOrigin, uint8 generationType, string calldata metadataUri, bytes32 evidenceHash) external returns (uint256);
}

/// @title ProductionRegistry
/// @notice 생산 투입량을 제품·자투리·폐기로 보존하고 ProductItem과 배치를 연결한다.
contract ProductionRegistry is AccessControl {
    bytes32 public constant BRAND_ROLE = keccak256("BRAND_ROLE");
    uint8 private constant PRODUCTION_PURPOSE = 2;
    uint8 private constant PRODUCTION_REASON = 1;
    uint8 private constant SCRAP_GENERATION = 2;

    enum Status { NONE, DRAFT, SIGNING, CONFIRMED, CANCELLED }
    struct MaterialUse {
        uint256 tokenId; uint256 inputAmount; uint256 productUsed; uint256 scrap; uint256 waste;
        uint256 lockId; uint8 rootOrigin; string scrapMetadataUri; bytes32 scrapEvidenceHash; uint256 scrapTokenId;
    }
    struct Batch { address brand; uint256 productQuantity; bytes32 evidenceHash; Status status; uint64 createdAt; uint64 confirmedAt; }

    IProductionMaterialToken public immutable materialToken;
    uint256 public nextBatchId = 1;
    mapping(uint256 => Batch) private _batches;
    mapping(uint256 => MaterialUse[]) private _uses;
    mapping(uint256 => uint256[]) private _productItems;
    mapping(uint256 => uint256) public productItemBatch;
    mapping(uint256 => uint256[]) private _parentsByScrap;

    event ProductionBatchCreated(uint256 indexed batchId, address indexed brand, uint256 productQuantity, bytes32 evidenceHash, uint256 occurredAt);
    event ProductionPlanSubmitted(uint256 indexed batchId, address indexed brand, uint256 occurredAt);
    event MaterialConsumed(uint256 indexed materialId, uint256 indexed batchId, uint256 amount, address indexed brand, uint256 occurredAt);
    event ProductLinked(uint256 indexed batchId, uint256 indexed productItemId, address indexed brand, uint256 occurredAt);
    event ProductionScrapCreated(uint256 indexed batchId, uint256 indexed parentMaterialId, uint256 indexed scrapMaterialId, uint256 amount, address brand, uint256 occurredAt);
    event ProductionConfirmed(uint256 indexed batchId, address indexed brand, uint256 productQuantity, bytes32 evidenceHash, uint256 occurredAt);

    constructor(address materialTokenAddress, address admin) {
        require(materialTokenAddress != address(0) && admin != address(0), "Production: zero address");
        materialToken = IProductionMaterialToken(materialTokenAddress);
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(BRAND_ROLE, admin);
    }

    function createBatch(uint256 productQuantity) external onlyRole(BRAND_ROLE) returns (uint256 batchId) {
        require(productQuantity > 0, "Production: invalid batch");
        batchId = nextBatchId++;
        _batches[batchId] = Batch(msg.sender, productQuantity, bytes32(0), Status.DRAFT, uint64(block.timestamp), 0);
        emit ProductionBatchCreated(batchId, msg.sender, productQuantity, bytes32(0), block.timestamp);
    }

    function submitPlanAndLock(uint256 batchId, bytes32 evidenceHash, uint256[] calldata tokenIds, uint256[] calldata inputAmounts, uint256[] calldata productUsed, uint256[] calldata scrap, uint256[] calldata waste, string[] calldata scrapMetadataUris, bytes32[] calldata scrapEvidenceHashes) external {
        Batch storage batch = _batch(batchId);
        require(msg.sender == batch.brand, "Production: caller is not brand");
        require(batch.status == Status.DRAFT, "Production: invalid status");
        require(evidenceHash != bytes32(0), "Production: evidence is empty");
        uint256 length = tokenIds.length;
        require(length > 0 && length == inputAmounts.length && length == productUsed.length && length == scrap.length && length == waste.length && length == scrapMetadataUris.length && length == scrapEvidenceHashes.length, "Production: invalid allocations");
        for (uint256 i; i < length; ++i) {
            require(inputAmounts[i] > 0, "Production: amount is zero");
            require(materialToken.availableBalance(msg.sender, tokenIds[i]) >= inputAmounts[i], "Production: insufficient material");
            for (uint256 j; j < i; ++j) require(tokenIds[j] != tokenIds[i], "Production: duplicate material");
            require(productUsed[i] + scrap[i] + waste[i] == inputAmounts[i], "Production: quantity not conserved");
            require(productUsed[i] > 0, "Production: product use is zero");
            if (scrap[i] > 0) require(scrapEvidenceHashes[i] != bytes32(0), "Production: scrap evidence is empty");
            uint256 lockId = materialToken.createMaterialLock(msg.sender, tokenIds[i], inputAmounts[i], PRODUCTION_PURPOSE, batchId);
            _uses[batchId].push(MaterialUse(tokenIds[i], inputAmounts[i], productUsed[i], scrap[i], waste[i], lockId, 0, scrapMetadataUris[i], scrapEvidenceHashes[i], 0));
        }
        batch.evidenceHash = evidenceHash; batch.status = Status.SIGNING;
        emit ProductionPlanSubmitted(batchId, batch.brand, block.timestamp);
    }

    function confirm(uint256 batchId) external {
        Batch storage batch = _batch(batchId);
        require(msg.sender == batch.brand, "Production: caller is not brand");
        require(batch.status == Status.SIGNING, "Production: invalid status");
        batch.status = Status.CONFIRMED; batch.confirmedAt = uint64(block.timestamp);
        MaterialUse[] storage uses = _uses[batchId];
        for (uint256 i; i < uses.length; ++i) {
            materialToken.consumeLockedMaterial(uses[i].lockId, batchId, PRODUCTION_PURPOSE, PRODUCTION_REASON);
            emit MaterialConsumed(uses[i].tokenId, batchId, uses[i].inputAmount, batch.brand, block.timestamp);
            if (uses[i].scrap > 0) {
                uint8 inheritedRootOrigin = _rootOrigin(uses[i].tokenId);
                uint256 scrapId = materialToken.mintDerivedMaterial(batch.brand, uses[i].scrap, inheritedRootOrigin, SCRAP_GENERATION, uses[i].scrapMetadataUri, uses[i].scrapEvidenceHash);
                uses[i].scrapTokenId = scrapId; _parentsByScrap[scrapId].push(uses[i].tokenId);
                emit ProductionScrapCreated(batchId, uses[i].tokenId, scrapId, uses[i].scrap, batch.brand, block.timestamp);
            }
        }
        emit ProductionConfirmed(batchId, batch.brand, batch.productQuantity, batch.evidenceHash, block.timestamp);
    }

    function linkProductItems(uint256 batchId, uint256[] calldata productItemIds) external {
        Batch storage batch = _batch(batchId);
        require(msg.sender == batch.brand, "Production: caller is not brand");
        require(batch.status == Status.CONFIRMED, "Production: batch is not confirmed");
        require(_productItems[batchId].length == 0, "Production: products already linked");
        require(productItemIds.length == batch.productQuantity, "Production: product count mismatch");
        for (uint256 i; i < productItemIds.length; ++i) {
            require(productItemIds[i] > 0 && productItemBatch[productItemIds[i]] == 0, "Production: product already linked");
            for (uint256 j; j < i; ++j) require(productItemIds[j] != productItemIds[i], "Production: duplicate product");
            productItemBatch[productItemIds[i]] = batchId; _productItems[batchId].push(productItemIds[i]);
            emit ProductLinked(batchId, productItemIds[i], batch.brand, block.timestamp);
        }
    }

    function getBatch(uint256 batchId) external view returns (Batch memory) { return _batches[batchId]; }
    function getMaterialUses(uint256 batchId) external view returns (MaterialUse[] memory) { return _uses[batchId]; }
    function getProductItems(uint256 batchId) external view returns (uint256[] memory) { return _productItems[batchId]; }
    function parentsOfScrap(uint256 scrapId) external view returns (uint256[] memory) { return _parentsByScrap[scrapId]; }
    function _rootOrigin(uint256 tokenId) private view returns (uint8) { return materialToken.rootOriginOf(tokenId); }
    function _batch(uint256 batchId) private view returns (Batch storage batch) { batch = _batches[batchId]; require(batch.status != Status.NONE, "Production: batch does not exist"); }
}
