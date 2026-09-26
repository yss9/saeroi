-- 소재 거래에 결제 마감을 둔다.
--
-- 왜 필요한가
-- ─────────────────────────────────────────────────────────────────────
-- 수량은 결제가 아니라 <b>구매 신청</b> 시점에 깎인다(TradeCommandService.create ->
-- listing.reduceAvailable). 되돌리는 경로는 PaymentService.cancel 하나뿐이고, 화면이
-- 그것을 부르는 자리도 카카오페이가 "취소/실패" 로 돌아왔을 때 한 곳뿐이다.
--
-- 그래서 결제창까지 가지 않고 탭을 닫으면 그 수량이 영원히 잠긴다. 남은 양을 전부
-- 사려던 거래였다면 판매 글이 그대로 품절로 보인다.
--
-- product_order·processing_job·processing_request 에는 이미 결제 마감과 만료 배치가
-- 있다. material_trade 만 없었다.

ALTER TABLE material_trade
    ADD COLUMN payment_deadline DATETIME NULL AFTER payment_amount;

-- 이미 잠겨 있는 거래를 푼다.
-- ─────────────────────────────────────────────────────────────────────
-- 새 컬럼은 NULL 이라 배치가 건드리지 않는다(쿼리가 `payment_deadline is not null`).
-- 그대로 두면 <b>이 마이그레이션 이전에 잠긴 수량은 계속 잠겨 있다.</b>
--
-- 그래서 기존 CREATED 거래에 마감을 소급해 넣는다. 생성 시각 + 30분이므로 대부분 이미
-- 지난 시각이 되고, 배치가 다음 주기(60초)에 정리하면서 판매 글 수량을 되돌린다.
--
-- <b>CREATED 만 손댄다.</b> PAID 이후는 결제가 끝난 거래라 마감이 의미 없고, 값을 넣으면
-- 배치 쿼리가 상태로 한 번 더 거르더라도 "결제했는데 마감이 지났다" 는 읽기 어려운
-- 기록이 남는다.
UPDATE material_trade
SET payment_deadline = created_at + INTERVAL 30 MINUTE
WHERE status = 'CREATED'
  AND payment_deadline IS NULL;
