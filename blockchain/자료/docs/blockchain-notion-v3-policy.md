# 블록체인 Notion v3 정책 반영

## 결제 경계

- 결제, 취소, 환불, 정산은 백엔드/PG와 DB에서만 처리한다.
- 스마트 컨트랙트에는 가격, 총액, PG 거래번호, 결제 성공 상태를 저장하지 않는다.
- 블록체인은 gram 단위 소재 무게 토큰의 잠금, 이전, 소각, 파생 발행과 계보만 보장한다.
- Marketplace 주문은 `CREATED`에서 오프체인 결제가 끝난 뒤 판매자가 `markShipped`를 호출하는 구조다.

## 주요 정책 반영

- 소재 잠금에 생성·해제·만료 시각과 `EXPIRED` 상태를 추가했다.
- 가공 Job에 공정 유형을 기록하고 출력별 품질 등급 `A/B/C`를 저장한다.
- 가공 출력은 입력 하나에 귀속되어 1→N만 지원하며 N→1 혼합은 지원하지 않는다.
- WASTE 원재료는 생산에 직접 사용할 수 없고 `PROCESSED` 단계를 거쳐야 한다.
- 생산 배치는 WASTE 원천 제품 투입량을 전체 제품 투입량으로 나눈 업사이클 비율을 basis point로 저장한다.
- RECOVERED 원천 유형과 이상 손실률 임계치는 MVP에서 제외한다.

## ABI 변경 및 연동 주의

- `Marketplace.createListing(materialId, quantity)`에서 가격 인자를 제거했다.
- `Marketplace.markPaid`, `calculateTotalPrice`, 결제 이벤트와 결제 필드를 제거했다.
- 분쟁 합의 함수는 `settleDispute`를 사용한다.
- `ProcessingRegistry.createJob`에 공정 유형 배열이 추가됐다.
- 가공 결과 함수는 `submitProcessingResult`, `approveProcessingResult`, `rejectProcessingResult`를 사용한다.
- 생산 확정 함수는 `confirmProduction`을 사용한다.
- 배포 후 백엔드는 새 `abi/*.json`으로 교체해야 하며, 기존 결제 트랜잭션 호출 코드는 제거해야 한다.
