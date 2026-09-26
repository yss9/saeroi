-- 생산을 제품 없이 확정할 수 있게 한다.
--
-- 브랜드의 실제 순서가 "생산을 끝내고 → 그 결과물에 제품 정보를 붙인다" 라,
-- 확정 전에 무엇을 만들었는지부터 정하라고 강제할 이유가 없다.
--
-- 기존 행은 전부 값을 가지고 있어 영향이 없다. NULL 을 허용하는 방향이라 되돌리기도 쉽다.
-- 제품은 PATCH /api/v1/production-batches/{batchId} 로 나중에 붙이며, 한 번 붙으면
-- 바뀔 수 없다(ProductItem.attachProductModel). 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
ALTER TABLE product_item
    MODIFY COLUMN product_model_id BIGINT NULL;
