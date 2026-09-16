-- SAEROI 시연 상품 전용 정리 스크립트.
--
-- 실행 대상: DEMO_PRODUCT_SEED_ENABLED=true 로 시연 데이터를 만든 개발·시연 DB
-- 실행 금지: 운영 DB
--
-- demo-catalog-* 이메일을 가진 전용 브랜드와 그 상품·주문·생산 데이터만 지운다.
-- 실제 브랜드가 등록한 상품은 이 이메일 규칙에 속하지 않아 대상이 아니다.

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS demo_brand_ids;
DROP TEMPORARY TABLE IF EXISTS demo_model_ids;
DROP TEMPORARY TABLE IF EXISTS demo_batch_ids;
DROP TEMPORARY TABLE IF EXISTS demo_order_ids;
DROP TEMPORARY TABLE IF EXISTS demo_canceled_order_ids;

CREATE TEMPORARY TABLE demo_brand_ids AS
SELECT id
FROM users
WHERE email LIKE 'demo-catalog-%@saeroi.local';

CREATE TEMPORARY TABLE demo_model_ids AS
SELECT id
FROM product_model
WHERE brand_id IN (SELECT id FROM demo_brand_ids);

CREATE TEMPORARY TABLE demo_batch_ids AS
SELECT id
FROM production_batch
WHERE product_model_id IN (SELECT id FROM demo_model_ids);

CREATE TEMPORARY TABLE demo_order_ids AS
SELECT id
FROM product_order
WHERE brand_id IN (SELECT id FROM demo_brand_ids);

CREATE TEMPORARY TABLE demo_canceled_order_ids AS
SELECT id, original_order_id
FROM canceled_product_order
WHERE brand_id IN (SELECT id FROM demo_brand_ids);

DELETE notification
FROM user_notification notification
JOIN demo_brand_ids demo_brand ON demo_brand.id = notification.recipient_id;

DELETE notification
FROM user_notification notification
JOIN demo_order_ids demo_order
  ON notification.reference_type = 'PRODUCT_ORDER'
 AND notification.reference_id = demo_order.id;

DELETE notification
FROM user_notification notification
JOIN demo_canceled_order_ids demo_order
  ON notification.reference_type = 'PRODUCT_ORDER'
 AND notification.reference_id = demo_order.original_order_id;

DELETE shipping
FROM product_shipping shipping
JOIN demo_order_ids demo_order ON demo_order.id = shipping.product_order_id;

DELETE order_item
FROM product_order_item order_item
JOIN demo_order_ids demo_order ON demo_order.id = order_item.product_order_id;

DELETE FROM product_order
WHERE id IN (SELECT id FROM demo_order_ids);

DELETE canceled_item
FROM canceled_product_order_item canceled_item
JOIN demo_canceled_order_ids demo_order
  ON demo_order.id = canceled_item.canceled_product_order_id;

DELETE FROM canceled_product_order
WHERE id IN (SELECT id FROM demo_canceled_order_ids);

DELETE FROM product_item
WHERE product_model_id IN (SELECT id FROM demo_model_ids);

DELETE output
FROM production_output output
JOIN production_input input_row ON input_row.id = output.production_input_id
JOIN demo_batch_ids demo_batch ON demo_batch.id = input_row.production_batch_id;

DELETE FROM production_input
WHERE production_batch_id IN (SELECT id FROM demo_batch_ids);

DELETE FROM production_batch
WHERE id IN (SELECT id FROM demo_batch_ids);

DELETE FROM product_model
WHERE id IN (SELECT id FROM demo_model_ids);

DELETE FROM refresh_token
WHERE user_id IN (SELECT id FROM demo_brand_ids);

DELETE FROM wallet_nonce
WHERE user_id IN (SELECT id FROM demo_brand_ids);

DELETE FROM user_wallet
WHERE user_id IN (SELECT id FROM demo_brand_ids);

DELETE FROM company_profile
WHERE user_id IN (SELECT id FROM demo_brand_ids);

DELETE FROM users
WHERE id IN (SELECT id FROM demo_brand_ids);

DROP TEMPORARY TABLE demo_canceled_order_ids;
DROP TEMPORARY TABLE demo_order_ids;
DROP TEMPORARY TABLE demo_batch_ids;
DROP TEMPORARY TABLE demo_model_ids;
DROP TEMPORARY TABLE demo_brand_ids;

COMMIT;
