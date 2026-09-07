# Blockchain Shop / Product Passport 정책

## 구현 범위

Shop 자체는 오프체인 기능이다. Solidity에서는 확정된 생산 배치와 개별 ProductItem의 연결 및
소재 이력을 검증할 수 있는 최소 증명만 제공한다. 별도 `Shop` 또는 결제 Contract를 만들지 않고
기존 `ProductionRegistry`를 사용한다.

## 온체인 데이터

- `ProductionBatch ID ↔ ProductItem ID` 연결
- Product를 만든 Brand Wallet
- Production 확정 시점의 업사이클 비율 스냅샷
- Production Evidence Manifest Hash
- Production 확정 시각
- Product에 사용된 Material과 gram 단위 사용량
- `ProductLinked` Event

`getProductPassportProof(productItemId)`로 연결 증명을 조회한 뒤, 반환된 Batch ID를 사용해
`getMaterialUses(batchId)`를 호출하면 소재 구성을 확인할 수 있다. 두 함수 모두 공개 `view`이므로
Consumer는 Wallet 연결이나 서명 없이 RPC로 조회할 수 있다.

## 오프체인 데이터

- 제품명, 설명, 카테고리, 이미지, 가격, 재고 및 판매 상태
- 주문, 배송 주소와 상태
- 카카오페이 PG 결제·취소·환불·정산 정보
- QR 이미지, Passport URL, 추측 불가능한 랜덤 `qrToken`

Shop 구매 시 Material은 다시 차감하지 않는다. Material Token 소비는 Production 확정 과정에서
한 번만 수행하며, 제품 판매·배송·결제는 새로운 온체인 Transaction을 만들지 않는다.

## 무결성 규칙

- 확정된 ProductionBatch에만 ProductItem을 연결할 수 있다.
- Batch를 생성한 Brand만 연결할 수 있다.
- ProductItem 수는 Batch의 생산 수량과 정확히 같아야 한다.
- 하나의 Batch에는 한 번만 ProductItem 목록을 연결할 수 있다.
- ProductItem ID는 전체 Batch에서 중복 연결할 수 없다.
- 연결되지 않은 ProductItem은 Passport 증명 조회에 실패한다.

## Notion v3 대응

- 16절: Production 확정 후 ProductItem 생성 및 QR 생성
- 17절: ProductItem → ProductionBatch → Material 역추적
- 18절: Product 상세·Marketplace·결제는 DB, Evidence Hash와 생산 이력은 Blockchain
