// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title OperationTracked
/// @notice 오프체인 작업과 온체인 실행을 연결하고 동일 업무의 중복 실행을 막는다.
/// @dev operationId는 백엔드가 트랜잭션 서명 전에 발급한다. 실행이 revert되면 mapping과
///      이벤트도 함께 롤백되므로 OperationRecorded는 성공한 실행에만 남는다.
abstract contract OperationTracked {
    mapping(bytes32 => bool) private _processedOperations;

    event OperationRecorded(
        bytes32 indexed operationId,
        bytes4 indexed selector,
        address indexed actor
    );

    modifier trackedOperation(bytes32 operationId) {
        require(operationId != bytes32(0), "Operation: zero id");
        require(!_processedOperations[operationId], "Operation: already processed");
        _processedOperations[operationId] = true;
        _;
        emit OperationRecorded(operationId, msg.sig, msg.sender);
    }

    function isOperationProcessed(bytes32 operationId) external view returns (bool) {
        return _processedOperations[operationId];
    }
}
