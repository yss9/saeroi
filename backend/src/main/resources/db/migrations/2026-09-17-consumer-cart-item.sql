-- 소비자의 장바구니.
--
-- 지금까지 장바구니는 프론트 메모리에만 있어 새로고침하면 사라졌다. 이 테이블이 그것을
-- 대체한다. 이번 범위는 저장과 조회까지이고, 장바구니로 주문하는 흐름은 다음 작업이다.
--
-- 수량을 지금 넣는다. 현재 주문 API 는 한 번에 1개만 주문하지만, 수량은 장바구니의
-- 본질이고 데이터가 쌓인 뒤에 컬럼을 더하는 편이 더 비싸다.
--
-- 담기는 재고 예약이 아니다. added_price 도 결제 기준이 아니라 가격 변동 안내용이다.
--
-- 새 테이블만 만들므로 기존 행과 기능에 영향이 없다.
-- 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
CREATE TABLE consumer_cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_model_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    added_price BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_consumer_cart_item PRIMARY KEY (id),
    -- 같은 상품이 두 행이 되는 것을 막는다. 조회 후 삽입만으로는 동시 요청이 함께 통과한다.
    CONSTRAINT uk_consumer_cart_item_user_product UNIQUE (user_id, product_model_id),
    CONSTRAINT fk_consumer_cart_item_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_consumer_cart_item_product FOREIGN KEY (product_model_id)
        REFERENCES product_model (id),
    CONSTRAINT chk_consumer_cart_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_consumer_cart_item_price CHECK (added_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
