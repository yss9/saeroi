-- 온체인 배치 번호를 담을 칸. 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
--
-- ProductionRegistry.createBatch 가 번호를 채번하고, 그 뒤의 submitPlanAndLock ·
-- confirmProduction · linkProductItems 가 전부 그 번호를 인자로 받는다. 서버가 들고 있지
-- 않으면 화면이 네 단계 내내 붙들고 있어야 하고 새로고침 한 번에 잃어버린다.
--
-- 소재는 token_id, 판매글은 chain_listing_id, 주문은 chain_trade_id 를 같은 이유로 갖고 있다.
ALTER TABLE production_batch
    ADD COLUMN chain_batch_id BIGINT NULL AFTER id,
    ADD CONSTRAINT uk_production_batch_chain_id UNIQUE (chain_batch_id);
