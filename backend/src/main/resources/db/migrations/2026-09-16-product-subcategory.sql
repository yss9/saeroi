-- 제품에 하위 분류(소비자 SHOP 2단 메뉴)를 더한다.
--
-- ProductCategory 를 쪼개지 않고 컬럼을 따로 두는 이유는 기존 데이터 때문이다.
-- CLOTHING 을 CLOTHING_TOP 같은 값들로 나누면 이미 팔린 주문과 여권이 가리키던 분류가
-- 사라진다. 상위 분류는 그대로 두고 아래를 덧붙인다.
--
-- NULL 을 허용한다. WALLET·ACCESSORY·HOME·ETC 는 2단 메뉴가 없고, 이 마이그레이션
-- 이전에 등록된 CLOTHING·BAG 제품도 값이 없다. 그 제품들은 상위 분류 목록에는 그대로
-- 나오고 2단 메뉴에서만 빠진다 — 임의의 값을 채우면 브랜드가 정하지 않은 분류가 된다.
-- 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
ALTER TABLE product_model
    ADD COLUMN subcategory VARCHAR(50) NULL AFTER category;

-- 소비자 공개 목록은 브랜드를 고르지 않고 status='ON_SALE' + 분류로 좁힌다.
-- 기존 idx_product_model_brand_status 는 brand_id 가 선두라 그 질의에 쓰이지 않는다.
ALTER TABLE product_model
    ADD INDEX idx_product_model_status_category (status, category, subcategory);
