# Processing Custody Hardening Design

## Goal

가공 소재 인수·반환·승인의 온체인 결과와 DB 읽기 모델을 일치시키고, 실제 업로드 증빙만 온체인에 고정하며, 반려 후 작업 종료 경로를 제공한다.

## Flow

1. Frontend는 MetaMask 트랜잭션 Receipt 성공을 확인한 뒤 txHash만 Backend에 전달한다.
2. Backend는 설정된 Besu RPC에서 Receipt를 다시 조회하고 성공 상태, ProcessingRegistry 주소, 호출자 지갑, Job ID와 기대 이벤트를 검증한다.
3. Processor 수령 시 새 Lock ID, 최종 승인 시 Child Token ID는 요청 바디가 아니라 검증된 이벤트에서 추출한다.
4. 반환 발송은 해당 Processing Job에 Processor가 업로드한 살아 있는 증빙 파일을 선택한다. Backend가 저장된 SHA-256과 Receipt의 evidenceHash가 같은지 검증한다.
5. 반려된 작업은 Processor가 원소재 반환 증빙을 선택해 온체인 반환을 실행할 수 있다. Processor Lock을 소비해 Brand에게 원소재를 돌려주고 Job을 CANCELED로 종료한다.
6. 최종 승인 Receipt가 검증되면 Processor 입력 잔액과 Lock을 소비하고, 온체인에 이미 발행된 Child Material을 DB에 연결한 뒤 Brand material_balance를 생성한다.

## Consistency and retry

- 각 Backend 반영은 Job 행과 소재 잔액·Lock 행을 비관적 잠금으로 읽고 한 DB 트랜잭션에서 처리한다.
- 처리된 txHash와 같은 요청은 현재 응답을 반환한다. 다른 txHash로 동일 단계를 재실행하면 거절한다.
- Receipt 검증 실패 시 DB는 변경하지 않는다.
- Blockchain과 DB는 하나의 원자 트랜잭션이 될 수 없으므로, 온체인 성공 후 동일 txHash 재시도로 DB 반영을 복구한다.

## Deployment

ProcessingRegistry를 재배포한 뒤 ABI, 주소, MaterialToken의 Lock Manager/Workflow 권한을 같은 배포분으로 갱신한다. 기존 Job은 이전 컨트랙트에서 계속 처리하거나 명시적으로 종료하며 새 주소로 자동 이관하지 않는다.

