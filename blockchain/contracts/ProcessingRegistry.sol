// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {OperationTracked} from "./OperationTracked.sol";

interface IProcessingMaterialToken {
    struct MaterialLockSnapshot {
        uint256 id;
        address operator;
        address owner;
        uint256 tokenId;
        uint256 initialAmount;
        uint256 remainingAmount;
        uint8 purpose;
        uint256 referenceId;
        uint8 status;
        uint64 createdAt;
        uint64 releasedAt;
        uint64 expiresAt;
    }

    function balanceOf(address account, uint256 id) external view returns (uint256);
    function availableBalance(address owner, uint256 tokenId) external view returns (uint256);
    function rootOriginOf(uint256 tokenId) external view returns (uint8);
    function createMaterialLock(address owner, uint256 tokenId, uint256 amount, uint8 purpose, uint256 referenceId) external returns (uint256);
    function releaseMaterialLock(uint256 lockId, uint256 amount) external;
    function consumeMaterialLock(uint256 lockId, address to, uint256 amount) external;
    function consumeLockedMaterial(uint256 lockId, uint256 referenceId, uint8 purpose, uint8 reason) external returns (uint256, uint256);
    function materialLockInfo(uint256 lockId) external view returns (MaterialLockSnapshot memory);
    function mintDerivedMaterial(address to, uint256 amount, uint8 rootOrigin, uint8 generationType, uint8 grade, string calldata metadataUri, bytes32 evidenceHash) external returns (uint256);
}

/// @title ProcessingRegistry
/// @notice 배송부터 결과 승인, 입력 소각, 다중 산출물 발행까지 가공 전 과정을 원자적으로 관리한다.
contract ProcessingRegistry is AccessControl, OperationTracked {
    bytes32 public constant BRAND_ROLE = keccak256("BRAND_ROLE");
    bytes32 public constant PROCESSOR_ROLE = keccak256("PROCESSOR_ROLE");
    uint8 private constant PROCESSING_PURPOSE = 1;
    uint8 private constant PROCESSING_REASON = 0;
    uint8 private constant PROCESSED_GENERATION = 1;

    enum Status {
        NONE,
        REQUESTED,
        ACCEPTED,
        SHIPPED,
        RECEIVED,
        PROCESSING,
        RESULT_SUBMITTED,
        CONFIRMED,
        RESULT_REJECTED,
        DECLINED,
        CANCELED,
        RETURN_SHIPPED,
        REJECTED_INPUT_RETURNED
    }
    enum ProcessingType { CLEANING, DISASSEMBLY, SORTING, CUTTING, SHREDDING, COLOR_PROCESSING, SURFACE_FINISHING, REMANUFACTURING }
    enum Grade { NONE, A, B, C }
    struct Input { uint256 tokenId; uint256 amount; uint256 brandLockId; uint256 processorLockId; uint8 rootOrigin; }
    struct Output { uint256 inputIndex; uint256 amount; Grade grade; string metadataUri; bytes32 evidenceHash; uint256 tokenId; }
    struct Job { address brand; address processor; bytes32 shippingEvidenceHash; bytes32 resultEvidenceHash; bytes32 returnShippingEvidenceHash; Status status; uint64 createdAt; uint64 updatedAt; }

    IProcessingMaterialToken public immutable materialToken;
    uint256 public nextJobId = 1;
    mapping(uint256 => Job) private _jobs;
    mapping(uint256 => Input[]) private _inputs;
    mapping(uint256 => Output[]) private _outputs;
    mapping(uint256 => ProcessingType[]) private _processingTypes;
    mapping(uint256 => uint256[]) private _parentsByChild;

    event ProcessingJobCreated(uint256 indexed jobId, address indexed brand, address indexed processor, uint256 inputAmount, uint256 occurredAt);
    event ProcessingJobAccepted(uint256 indexed jobId, address indexed processor, uint256 occurredAt);
    event ProcessingJobDeclined(uint256 indexed jobId, address indexed processor, uint256 occurredAt);
    event ProcessingShipped(uint256 indexed jobId, address indexed brand, bytes32 evidenceHash, uint256 occurredAt);
    event ProcessingReceived(uint256 indexed jobId, address indexed processor, uint256 occurredAt);
    event ProcessingInputCustodyTransferred(uint256 indexed jobId, uint256 indexed tokenId, uint256 oldLockId, uint256 newLockId, uint256 amount, address from, address to);
    event ProcessingResultSubmitted(uint256 indexed jobId, address indexed processor, bytes32 evidenceHash, uint256 outputAmount, uint256 occurredAt);
    event ProcessingReturnShipped(uint256 indexed jobId, address indexed processor, bytes32 evidenceHash, uint256 occurredAt);
    event ProcessingRejectedInputReturned(uint256 indexed jobId, address indexed processor, address indexed brand, bytes32 evidenceHash, uint256 occurredAt);
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

    function createJob(address processor, uint256[] calldata tokenIds, uint256[] calldata amounts, ProcessingType[] calldata processingTypes, bytes32 operationId) external onlyRole(BRAND_ROLE) trackedOperation(operationId) returns (uint256 jobId) {
        require(processor != address(0), "Processing: processor is zero");
        require(hasRole(PROCESSOR_ROLE, processor), "Processing: processor role required");
        require(tokenIds.length > 0 && tokenIds.length == amounts.length, "Processing: invalid inputs");
        require(processingTypes.length > 0, "Processing: process type is empty");
        jobId = nextJobId++;
        uint256 total;
        for (uint256 i; i < tokenIds.length; ++i) {
            require(amounts[i] > 0, "Processing: amount is zero");
            require(materialToken.availableBalance(msg.sender, tokenIds[i]) >= amounts[i], "Processing: insufficient material");
            for (uint256 j; j < i; ++j) require(tokenIds[j] != tokenIds[i], "Processing: duplicate material");
            uint256 lockId = materialToken.createMaterialLock(msg.sender, tokenIds[i], amounts[i], PROCESSING_PURPOSE, jobId);
            _inputs[jobId].push(Input(tokenIds[i], amounts[i], lockId, 0, materialToken.rootOriginOf(tokenIds[i])));
            total += amounts[i];
        }
        for (uint256 i; i < processingTypes.length; ++i) {
            for (uint256 j; j < i; ++j) require(processingTypes[j] != processingTypes[i], "Processing: duplicate process type");
            _processingTypes[jobId].push(processingTypes[i]);
        }
        _jobs[jobId] = Job(msg.sender, processor, bytes32(0), bytes32(0), bytes32(0), Status.REQUESTED, uint64(block.timestamp), uint64(block.timestamp));
        emit ProcessingJobCreated(jobId, msg.sender, processor, total, block.timestamp);
    }

    /// @notice 지정된 Processor가 가공 요청을 수락해야 Brand가 소재를 발송할 수 있다.
    function acceptJob(uint256 jobId, bytes32 operationId) external onlyRole(PROCESSOR_ROLE) trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.REQUESTED, "Processing: invalid status");
        job.status = Status.ACCEPTED;
        job.updatedAt = uint64(block.timestamp);
        emit ProcessingJobAccepted(jobId, msg.sender, block.timestamp);
    }

    /// @notice 지정된 Processor가 요청을 거절하면 Job을 종료하고 입력 소재 잠금을 해제한다.
    function rejectJob(uint256 jobId, bytes32 operationId) external onlyRole(PROCESSOR_ROLE) trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.REQUESTED, "Processing: invalid status");
        job.status = Status.DECLINED;
        job.updatedAt = uint64(block.timestamp);
        _releaseInputs(jobId);
        emit ProcessingJobDeclined(jobId, msg.sender, block.timestamp);
    }

    function cancelJob(uint256 jobId, bytes32 operationId) external trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(
            job.status == Status.REQUESTED || job.status == Status.ACCEPTED,
            "Processing: invalid status"
        );
        require(msg.sender == job.brand || hasRole(DEFAULT_ADMIN_ROLE, msg.sender), "Processing: caller cannot cancel");
        job.status = Status.CANCELED; job.updatedAt = uint64(block.timestamp);
        _releaseInputs(jobId);
    }

    function markShipped(uint256 jobId, bytes32 evidenceHash, bytes32 operationId) external trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.brand, "Processing: caller is not brand");
        require(job.status == Status.ACCEPTED, "Processing: invalid status");
        require(evidenceHash != bytes32(0), "Processing: evidence is empty");
        job.shippingEvidenceHash = evidenceHash; job.status = Status.SHIPPED; job.updatedAt = uint64(block.timestamp);
        emit ProcessingShipped(jobId, msg.sender, evidenceHash, block.timestamp);
    }

    function confirmReceipt(uint256 jobId, bytes32 operationId) external onlyRole(PROCESSOR_ROLE) trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.SHIPPED, "Processing: invalid status");
        Input[] storage inputs = _inputs[jobId];
        for (uint256 i; i < inputs.length; ++i) {
            uint256 oldLockId = inputs[i].brandLockId;
            require(inputs[i].processorLockId == 0, "Processing: processor lock already exists");
            materialToken.consumeMaterialLock(oldLockId, job.processor, inputs[i].amount);
            uint256 newLockId = materialToken.createMaterialLock(
                job.processor, inputs[i].tokenId, inputs[i].amount, PROCESSING_PURPOSE, jobId
            );
            inputs[i].processorLockId = newLockId;
            emit ProcessingInputCustodyTransferred(
                jobId, inputs[i].tokenId, oldLockId, newLockId, inputs[i].amount, job.brand, job.processor
            );
        }
        job.status = Status.RECEIVED; job.updatedAt = uint64(block.timestamp);
        emit ProcessingReceived(jobId, msg.sender, block.timestamp);
    }

    function startProcessing(uint256 jobId, bytes32 operationId) external onlyRole(PROCESSOR_ROLE) trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.RECEIVED, "Processing: invalid status");
        job.status = Status.PROCESSING; job.updatedAt = uint64(block.timestamp);
    }

    function submitProcessingResult(uint256 jobId, uint256[] calldata inputIndexes, uint256[] calldata amounts, Grade[] calldata grades, string[] calldata metadataUris, bytes32[] calldata evidenceHashes, bytes32 resultEvidenceHash, bytes32 operationId) external onlyRole(PROCESSOR_ROLE) trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(
            job.status == Status.PROCESSING || job.status == Status.RESULT_REJECTED,
            "Processing: invalid status"
        );
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

    function markReturnShipped(uint256 jobId, bytes32 evidenceHash, bytes32 operationId) external onlyRole(PROCESSOR_ROLE) trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.RESULT_SUBMITTED, "Processing: invalid status");
        require(evidenceHash != bytes32(0), "Processing: evidence is empty");
        Input[] storage inputs = _inputs[jobId];
        for (uint256 i; i < inputs.length; ++i) _requireProcessorLock(jobId, job.processor, inputs[i]);
        job.returnShippingEvidenceHash = evidenceHash;
        job.status = Status.RETURN_SHIPPED;
        job.updatedAt = uint64(block.timestamp);
        emit ProcessingReturnShipped(jobId, msg.sender, evidenceHash, block.timestamp);
    }

    /// @notice 결과 반려 후 작업을 중단할 때 잠긴 원소재를 Brand에게 반환하고 Job을 종료한다.
    function returnRejectedInput(uint256 jobId, bytes32 evidenceHash, bytes32 operationId) external onlyRole(PROCESSOR_ROLE) trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.processor, "Processing: caller is not processor");
        require(job.status == Status.RESULT_REJECTED, "Processing: invalid status");
        require(evidenceHash != bytes32(0), "Processing: evidence is empty");
        Input[] storage inputs = _inputs[jobId];
        for (uint256 i; i < inputs.length; ++i) {
            _requireProcessorLock(jobId, job.processor, inputs[i]);
            materialToken.consumeMaterialLock(inputs[i].processorLockId, job.brand, inputs[i].amount);
        }
        job.returnShippingEvidenceHash = evidenceHash;
        job.status = Status.REJECTED_INPUT_RETURNED;
        job.updatedAt = uint64(block.timestamp);
        emit ProcessingRejectedInputReturned(jobId, msg.sender, job.brand, evidenceHash, block.timestamp);
    }

    function approveProcessingResult(uint256 jobId, bytes32 operationId) external trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.brand, "Processing: caller is not brand");
        require(job.status == Status.RETURN_SHIPPED, "Processing: invalid status");
        uint256 inTotal = inputTotal(jobId); uint256 outTotal = outputTotal(jobId);
        Input[] storage inputs = _inputs[jobId];
        for (uint256 i; i < inputs.length; ++i) _requireProcessorLock(jobId, job.processor, inputs[i]);
        job.status = Status.CONFIRMED; job.updatedAt = uint64(block.timestamp);
        for (uint256 i; i < inputs.length; ++i) materialToken.consumeLockedMaterial(inputs[i].processorLockId, jobId, PROCESSING_PURPOSE, PROCESSING_REASON);
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

    function rejectProcessingResult(uint256 jobId, bytes32 operationId) external trackedOperation(operationId) {
        Job storage job = _job(jobId);
        require(msg.sender == job.brand, "Processing: caller is not brand");
        require(job.status == Status.RESULT_SUBMITTED, "Processing: invalid status");
        job.status = Status.RESULT_REJECTED; job.updatedAt = uint64(block.timestamp);
        delete _outputs[jobId];
        emit ProcessingRejected(jobId, job.brand, block.timestamp);
    }

    function inputTotal(uint256 jobId) public view returns (uint256 total) { Input[] storage values = _inputs[jobId]; for (uint256 i; i < values.length; ++i) total += values[i].amount; }
    function outputTotal(uint256 jobId) public view returns (uint256 total) { Output[] storage values = _outputs[jobId]; for (uint256 i; i < values.length; ++i) total += values[i].amount; }
    function getJob(uint256 jobId) external view returns (Job memory) { return _jobs[jobId]; }
    function getInputs(uint256 jobId) external view returns (Input[] memory) { return _inputs[jobId]; }
    function getOutputs(uint256 jobId) external view returns (Output[] memory) { return _outputs[jobId]; }
    function getProcessingTypes(uint256 jobId) external view returns (ProcessingType[] memory) { return _processingTypes[jobId]; }
    function parentsOf(uint256 childId) external view returns (uint256[] memory) { return _parentsByChild[childId]; }
    function _releaseInputs(uint256 jobId) private { Input[] storage inputs = _inputs[jobId]; for (uint256 i; i < inputs.length; ++i) materialToken.releaseMaterialLock(inputs[i].brandLockId, inputs[i].amount); }
    function _requireProcessorLock(uint256 jobId, address processor, Input storage input) private view {
        require(input.processorLockId != 0, "Processing: processor lock is missing");
        IProcessingMaterialToken.MaterialLockSnapshot memory value = materialToken.materialLockInfo(input.processorLockId);
        require(value.status == 1, "Processing: processor lock is not active");
        require(value.owner == processor, "Processing: processor lock owner mismatch");
        require(value.tokenId == input.tokenId, "Processing: processor lock token mismatch");
        require(value.initialAmount == input.amount && value.remainingAmount == input.amount, "Processing: processor lock amount mismatch");
        require(value.purpose == PROCESSING_PURPOSE, "Processing: processor lock purpose mismatch");
        require(value.referenceId == jobId, "Processing: processor lock reference mismatch");
    }
    function _job(uint256 jobId) private view returns (Job storage job) { job = _jobs[jobId]; require(job.status != Status.NONE, "Processing: job does not exist"); }
}
