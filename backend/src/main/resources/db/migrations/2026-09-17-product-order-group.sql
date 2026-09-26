-- 한 번의 결제로 여러 브랜드의 주문을 만들 수 있게 한다.
--
-- 장바구니에 브랜드가 둘 이상 담기면 주문은 브랜드 수만큼 생긴다. 브랜드가 자기 주문만
-- 관리하는 기존 구조(배송·취소·정산 단위가 브랜드)를 그대로 두기 위해서다. 그런데 결제는
-- 한 번이어야 한다 - 브랜드마다 결제창이 뜨면 소비자 경험이 무너진다.
--
-- 그래서 결제의 주체를 주문 밖으로 꺼낸다. product_order 의 결제 컬럼들은 그대로 두고,
-- 단일 주문(상품 상세의 "바로 구매")은 order_group_id 가 NULL 인 채 지금 경로를 그대로 쓴다.
-- 돈이 오가는 기존 경로를 건드리지 않는 것이 이 설계의 핵심이다.
--
-- 기존 행은 order_group_id 가 NULL 이라 영향이 없다.
-- 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
CREATE TABLE product_order_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_code VARCHAR(30) NOT NULL,
    buyer_id BIGINT NOT NULL,
    total_price BIGINT NOT NULL,
    status ENUM('CREATED', 'PAID') NOT NULL DEFAULT 'CREATED',
    payment_method ENUM('MOCK', 'KAKAO_PAY') NOT NULL,
    payment_reference VARCHAR(100) NULL,
    payment_amount BIGINT NOT NULL DEFAULT 0,
    payment_deadline DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME NULL,
    CONSTRAINT pk_product_order_group PRIMARY KEY (id),
    CONSTRAINT uk_product_order_group_code UNIQUE (group_code),
    CONSTRAINT fk_product_order_group_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT chk_product_order_group_amounts CHECK (total_price >= 0 AND payment_amount >= 0),
    INDEX idx_product_order_group_buyer (buyer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE product_order
    ADD COLUMN order_group_id BIGINT NULL AFTER brand_id,
    ADD CONSTRAINT fk_product_order_group FOREIGN KEY (order_group_id)
        REFERENCES product_order_group (id),
    ADD INDEX idx_product_order_group (order_group_id);
