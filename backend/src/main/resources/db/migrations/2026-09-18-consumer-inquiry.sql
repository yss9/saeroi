-- 소비자 1:1 문의.
--
-- 지금까지 "문의하기"는 프론트 로컬 state에만 있어 제출해도 서버에 남지 않았고,
-- "문의내역"은 목데이터만 보여줬다. 이 테이블이 그 저장소가 된다.
--
-- product_order_id 는 선택이고(ETC 유형은 없어도 된다) FK 를 걸지 않는다 — 주문을
-- 취소하면 product_order 행 자체가 지워지고 canceled_product_order 로 옮겨지는데,
-- FK 를 걸면 그 주문에 대한 문의가 하나라도 있으면 취소가 막힌다. target_label 이
-- 등록 시점 상품명 스냅샷이라 원본 주문이 사라져도 "무엇에 대한 문의였는지"는
-- 그대로 남는다.
--
-- answer 는 이 프로젝트에 관리자/CS 도메인이 없어 API 로 채울 방법이 아직 없다 —
-- 값이 없으면 항상 "답변 대기" 다.
--
-- 새 테이블만 만들므로 기존 행과 기능에 영향이 없다.
-- 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
CREATE TABLE consumer_inquiry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    product_order_id BIGINT NULL,
    target_label VARCHAR(255) NULL,
    content VARCHAR(2000) NOT NULL,
    answer VARCHAR(2000) NULL,
    answered_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_consumer_inquiry PRIMARY KEY (id),
    CONSTRAINT fk_consumer_inquiry_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_consumer_inquiry_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
