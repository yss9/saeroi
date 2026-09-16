-- 생산 배치를 제품 모델 없이 열 수 있게 한다.
--
-- 실제 작업 순서가 "배치 생성 → 소재 선택 → 사용량 입력 → Lock → …" 인데,
-- 맨 앞에서 제품을 요구하면 브랜드가 무엇을 만들지를 소재도 고르기 전에 확정해야 한다.
--
-- 기존 행은 전부 값을 가지고 있어 영향이 없다. NULL 을 허용하는 방향이라 되돌리기도 쉽다.
-- 확정 시점의 필수 검사는 ProductionConfirmService 가 한다
-- (PRODUCTION_PRODUCT_MODEL_REQUIRED). 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
ALTER TABLE production_batch
    MODIFY COLUMN product_model_id BIGINT NULL;
