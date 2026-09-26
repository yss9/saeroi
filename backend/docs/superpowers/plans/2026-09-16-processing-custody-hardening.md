# Processing Custody Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 가공 소재의 온체인 인수·반환·승인과 DB 상태를 검증 가능한 방식으로 동기화한다.

**Architecture:** ProcessingRegistry가 토큰 이동을 원자적으로 처리하고, Spring Backend는 Besu Receipt 이벤트를 검증한 뒤 DB 읽기 모델에 멱등 반영한다. Frontend는 실제 업로드 증빙을 선택하고 MetaMask 확정 txHash만 Backend에 전달한다.

**Tech Stack:** Solidity 0.8.24, Hardhat, Spring Boot 3.5, Java 21, React, TypeScript, ethers v6

**Spec:** `docs/superpowers/specs/2026-09-16-processing-custody-hardening-design.md`

## Global Constraints

- API 경로에는 `/v1`을 새로 추가하지 않는다.
- 기존 Status 번호는 유지하고 새 상태는 마지막에 추가한다.
- 실제 업로드 파일의 SHA-256만 evidenceHash로 사용한다.
- Receipt 검증 실패 시 DB를 변경하지 않는다.

---

### Task 1: ProcessingRegistry 잠금 검증과 반려 종료

**Files:** `contracts/ProcessingRegistry.sol`, `test/WorkflowIntegration.ts`, ABI 산출물

- [ ] 반환 발송 전 ACTIVE Processor Lock 검증 실패 테스트를 추가하고 실패를 확인한다.
- [ ] 반려 후 원소재 반환·Job 종료 테스트를 추가하고 실패를 확인한다.
- [ ] `markReturnShipped` 잠금 검증과 `returnRejectedInput`을 구현한다.
- [ ] Hardhat 전체 테스트를 통과시킨다.

### Task 2: Backend Receipt 검증과 DB 동기화

**Files:** blockchain config/verifier, processing DTO/service/entity/schema, processing integration tests

- [ ] 위조 txHash·잘못된 이벤트·중복 재시도 테스트를 추가하고 실패를 확인한다.
- [ ] JSON-RPC Receipt 검증기를 구현해 새 Lock ID와 Child Token ID를 이벤트에서 추출한다.
- [ ] 실제 증빙 파일 소유권과 SHA-256을 검증한다.
- [ ] 온체인 모드에서도 Child Material의 Brand balance를 생성한다.
- [ ] 반려 원소재 반환 시 Processor→Brand DB 잔액과 Lock을 멱등 반영한다.
- [ ] Backend 전체 테스트를 통과시킨다.

### Task 3: Frontend 실제 증빙 선택과 종료 플로우

**Files:** processing API/hook/components/tests, ProcessingRegistry ABI

- [ ] 반환 증빙 선택과 반려 종료 UI 테스트를 추가하고 실패를 확인한다.
- [ ] 업로드된 증빙 파일 ID와 fileHash를 사용해 반환 트랜잭션을 실행한다.
- [ ] 요청 바디에서 임의 evidenceHash, Lock ID, Child Token ID를 제거한다.
- [ ] Frontend 전체 테스트와 빌드를 통과시킨다.

### Task 4: 통합 검증과 Git 반영

- [ ] 각 원격 develop을 기능 브랜치에 병합한다.
- [ ] 세 저장소 전체 테스트를 다시 실행한다.
- [ ] Convention 형식의 커밋을 생성하고 기능 브랜치를 push한다.
