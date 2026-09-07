// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

interface IProcessingMaterialToken {
    function balanceOf(address account, uint256 id) external view returns (uint256);
    function availableBalance(address owner, uint256 tokenId) external view returns (uint256);
    function rootOriginOf(uint256 tokenId) external view returns (uint8);
    function createMaterialLock(address owner, uint256 tokenId, uint256 amount, uint8 purpose, uint256 referenceId) external returns (uint256);
    function releaseMaterialLock(uint256 lockId, uint256 amount) external;
    function consumeLockedMaterial(uint256 lockId, uint256 referenceId, uint8 purpose, uint8 reason) external returns (uint256, uint256);
    function mintDerivedMaterial(address to, uint256 amount, uint8 rootOrigin, uint8 generationType, uint8 grade, string calldata metadataUri, bytes32 evidenceHash) external returns (uint256);
}

/// @title ProcessingRegistry
/// @notice 배송부터 결과 승인, 입력 소각, 다중 산출물 발행까지 가공 전 과정을 원자적으로 관리한다.
contract ProcessingRegistry is AccessControl {
    bytes32 public constant BRAND_ROLE = keccak256("BRAND_ROLE");
    bytes32 public constant PROCESSOR_ROLE = keccak256("PROCESSOR_ROLE");
    uint8 private constant PROCESSING_PURPOSE = 1;
    uint8 private constant PROCESSING_REASON = 0;
    uint8 private constant PROCESSED_GENERATION = 1;

    enum Status { NONE, CREATED, SHIPPED, RECEIVED, PROCESSING, RESULT_SUBMITTED, CONFIRMED, REJECTED, CANCELED }
    enum ProcessingType { CLEANING, DISASSEMBLY, SORTING, CUTTING, SHREDDING, COLOR_PROCESSING, SURFACE_FINISHING, REMANUFACTURING }
    enum Grade { NONE, A, B, C }
    struct Input { uint256 tokenId; uint256 amount; uint256 lockId; uint8 rootOrigin; }
    struct Output { uint256 inputIndex; uint256 amount; Grade grade; string metadataUri; bytes32 evidenceHash; uint256 tokenId; }
    struct Job { address brand; address processor; bytes32 shippingEvidenceHash; bytes32 resultEvidenceHash; Status status; uint64 createdAt; uint64 updatedAt; }

    IProcessingMaterialToken public immutable materialToken;
    uint256 public nextJobId = 1;
    mapping(uint256 => Job) private _jobs;
    mapping(uint256 => Input[]) private _inputs;
    mapping(uint256 => Output[]) private _outputs;
    mapping(uint256 => ProcessingType[]) private _processingTypes;
    mapping(uint256 => uint256[]) private _parentsByChild;

    event ProcessingJobCreated(uint256 indexed jobId, address indexed brand, address indexed processor, uint256 inputAmount, uint256 occurredAt);
    event ProcessingShipped(uint256 indexed jobId, address indexed brand, bytes32 evidenceHash, uint256 occurredAt);
    event ProcessingReceived(uint256 indexed jobId, address indexed processor, uint256 occurredAt);
    event ProcessingResultSubmitted(uint256 indexed jobId, address indexed processor, bytes32 evidenceHash, uint256 outputAmount, uint256 occurredAt);
    event ProcessingConfirmed(uint256 indexed jobId, address indexed processor, address indexed brand, bytes32 evidenceHash, uint256 occurredAt);
    event ProcessingApproved(uint256 indexed jobId, address indexed brand, uint256 inputAmount, uint256 outputAmount, uint256 lossAmount, uint256 occurredAt);
    event ProcessingRejected(uint256 indexed jobId, address indexed brand, uint256 occurredAt);
    event MaterialProcessed(uint256 indexed jobId, uint256 indexed inputMaterialId, uint256 indexed outputMaterialId, uint256 outputAmount, address processor, address brand, uint256 occurredAt);

    constructor(address materialTokenAddress, address admin) {
        require(materialTokenAddress != address(0) && admin != address(0), "Processing: zero address");
        materialToken = IProcessingMaterialToken(materialTokenAddress);
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(BRAND_ROLE, admin);
        _grantRole(PROCESSOR_ROLE, admin);
    }

    function createJob(address processor, uint256[] calldata tokenIds, uint256[] calldata amounts, ProcessingType[] calldata processingTypes) external onlyRole(BRAND_ROLE) returns (uint256 jobId) {
        require(processor != address(0), "Processing: processor is zero");
        require(tokenIds.length > 0 && tokenIds.length == amounts.length, "Processing: invalid inputs");
        require(processingTypes.length > 0, "Processing: process type is empty");
        jobId = nextJobId++;
        uint256 total;
        for (uint256 i; i < tokenIds.length; ++i) {
            require(amounts[i] > 0, "Processing: amount is zero");
            require(materialToken.availableBalance(msg.sender, tokenIds[i]) >= amounts[i], "Processing: insufficient material");
            for (uint256 j; j < i; ++j) require(tokenIds[j] != tokenIds[i], "Processing: duplicate material");
            uint256 lockId = materialToken.createMaterialLock(msg.sender, tokenIds[i], amounts[i], PROCESSING_PURPOSE, jobId);
            _inputs[jobId].push(Input(tokenIds[i], amounts[i], lockId, materialToken.rootOriginOf(tokenIds[i])));
            total += amounts[i];
        }
        for (uint256 i; i < processingTypes.length; ++i) {
            for (uint256 j; j < i; ++j) require(processingTypes[j] != processingTypes[i], "Processing: duplicate process type");
            _processingTypes[jobId].push(processingTypes[i]);
        }
        _jobs[jobId] = Job(msg.sender, processor, bytes32(0), bytes32(0), Status.CREATED, uint64(block.timestamp), uint64(block.timestamp));
        emit ProcessingJobCreated(jobId, msg.sender, processor, total, block.timestamp);
    }

    function cancelJob(uint256 jobId) external {
        Job storage job = _job(jobId);
        require(job.status == Status.CREATED, "Processing: invalid status");
        require(msg.sender == job.brand || hasRole(DEFAULT_ADMIN_ROLE, msg.sender), "Processing: caller cannot cancel");
        job.status = Status.CANCELED; job.updatedAt = uint64(block.timestamp);
        _releaseInputs(jobId);
    }

    function markShipped(uint256 jobId, bytes32 evidenceHash) external {
        Job storage job = _job(jobId);
        require(msg.sender == job.brand, "Processing: caller is not brand");
        require(job.status == Status.CREATED, "Processing: invalid status");
        require(evidenceHash != bytes32(0), "Processing: evidence is empty");
        job.shippingEvidenceHash = evidenceHash; job.status = Status.SHIPPED; job.updatedAt = uint64(block.timestamp);
        emit ProcessingShipped(jobId, msg.sender, evidenceHash, block.timestamp);
    }

    function confirmReceipt(uint256 jobId) external onlyRole(PROCESSOR_ROLE) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.SHIPPED, "Processing: invalid status");
        job.status = Status.RECEIVED; job.updatedAt = uint64(block.timestamp);
        emit ProcessingReceived(jobId, msg.sender, block.timestamp);
    }

    function startProcessing(uint256 jobId) external onlyRole(PROCESSOR_ROLE) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.RECEIVED, "Processing: invalid status");
        job.status = Status.PROCESSING; job.updatedAt = uint64(block.timestamp);
    }

    function submitProcessingResult(uint256 jobId, uint256[] calldata inputIndexes, uint256[] calldata amounts, Grade[] calldata grades, string[] calldata metadataUris, bytes32[] calldata evidenceHashes, bytes32 resultEvidenceHash) external onlyRole(PROCESSOR_ROLE) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.PROCESSING, "Processing: invalid status");
        require(amounts.length > 0 && amounts.length == inputIndexes.length && amounts.length == grades.length && amounts.length == metadataUris.length && amounts.length == evidenceHashes.length, "Processing: invalid outputs");
        require(resultEvidenceHash != bytes32(0), "Processing: evidence is empty");
        uint256[] memory totals = new uint256[](_inputs[jobId].length);
        uint256 submittedTotal;
        for (uint256 i; i < amounts.length; ++i) {
            require(amounts[i] > 0 && grades[i] != Grade.NONE && evidenceHashes[i] != bytes32(0), "Processing: invalid output");
            require(inputIndexes[i] < _inputs[jobId].length, "Processing: invalid input reference");
            totals[inputIndexes[i]] += amounts[i];
            require(totals[inputIndexes[i]] <= _inputs[jobId][inputIndexes[i]].amount, "Processing: output exceeds input");
            _outputs[jobId].push(Output(inputIndexes[i], amounts[i], grades[i], metadataUris[i], evidenceHashes[i], 0));
            submittedTotal += amounts[i];
        }
        job.resultEvidenceHash = resultEvidenceHash; job.status = Status.RESULT_SUBMITTED; job.updatedAt = uint64(block.timestamp);
        emit ProcessingResultSubmitted(jobId, msg.sender, resultEvidenceHash, submittedTotal, block.timestamp);
    }

    function approveProcessingResult(uint256 jobId) external {
        Job storage job = _job(jobId);
        require(msg.sender == job.brand, "Processing: caller is not brand");
        require(job.status == Status.RESULT_SUBMITTED, "Processing: invalid status");
        uint256 inTotal = inputTotal(jobId); uint256 outTotal = outputTotal(jobId);
        job.status = Status.CONFIRMED; job.updatedAt = uint64(block.timestamp);
        Input[] storage inputs = _inputs[jobId];
        for (uint256 i; i < inputs.length; ++i) materialToken.consumeLockedMaterial(inputs[i].lockId, jobId, PROCESSING_PURPOSE, PROCESSING_REASON);
        Output[] storage outputs = _outputs[jobId];
        for (uint256 i; i < outputs.length; ++i) {
            Input storage parent = inputs[outputs[i].inputIndex];
            uint256 childId = materialToken.mintDerivedMaterial(job.brand, outputs[i].amount, parent.rootOrigin, PROCESSED_GENERATION, uint8(outputs[i].grade), outputs[i].metadataUri, outputs[i].evidenceHash);
            outputs[i].tokenId = childId;
            _parentsByChild[childId].push(parent.tokenId);
            emit MaterialProcessed(jobId, parent.tokenId, childId, outputs[i].amount, job.processor, job.brand, block.timestamp);
        }
        emit ProcessingApproved(jobId, job.brand, inTotal, outTotal, inTotal - outTotal, block.timestamp);
        emit ProcessingConfirmed(jobId, job.processor, job.brand, job.resultEvidenceHash, block.timestamp);
    }

    function rejectProcessingResult(uint256 jobId) external {
        Job storage job = _job(jobId);
        require(msg.sender == job.brand, "Processing: caller is not brand");
        require(job.status == Status.RESULT_SUBMITTED, "Processing: invalid status");
        job.status = Status.REJECTED; job.updatedAt = uint64(block.timestamp);
        _releaseInputs(jobId);
        emit ProcessingRejected(jobId, job.brand, block.timestamp);
    }

    function inputTotal(uint256 jobId) public view returns (uint256 total) { Input[] storage values = _inputs[jobId]; for (uint256 i; i < values.length; ++i) total += values[i].amount; }
    function outputTotal(uint256 jobId) public view returns (uint256 total) { Output[] storage values = _outputs[jobId]; for (uint256 i; i < values.length; ++i) total += values[i].amount; }
    function getJob(uint256 jobId) external view returns (Job memory) { return _jobs[jobId]; }
    function getInputs(uint256 jobId) external view returns (Input[] memory) { return _inputs[jobId]; }
    function getOutputs(uint256 jobId) external view returns (Output[] memory) { return _outputs[jobId]; }
    function getProcessingTypes(uint256 jobId) external view returns (ProcessingType[] memory) { return _processingTypes[jobId]; }
    function parentsOf(uint256 childId) external view returns (uint256[] memory) { return _parentsByChild[childId]; }
    function _releaseInputs(uint256 jobId) private { Input[] storage inputs = _inputs[jobId]; for (uint256 i; i < inputs.length; ++i) materialToken.releaseMaterialLock(inputs[i].lockId, inputs[i].amount); }
    function _job(uint256 jobId) private view returns (Job storage job) { job = _jobs[jobId]; require(job.status != Status.NONE, "Processing: job does not exist"); }
}
