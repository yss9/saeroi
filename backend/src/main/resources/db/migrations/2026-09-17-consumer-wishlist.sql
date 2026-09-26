-- 소비자의 찜 목록.
--
-- 지금까지 찜은 프론트 메모리에만 있어 새로고침하면 사라졌다. 이 테이블이 그것을 대체한다.
--
-- 찜 대상은 product_model 이다. product_item 은 같은 상품의 개별 재고라 서로 구별되지
-- 않는다 - 사용자는 "이 가방" 을 찜하는 것이지 "이 가방의 3번 개체" 를 찜하지 않는다.
--
-- 상품이 단종되거나 품절되거나 소비자에게 감추는 분류로 바뀌어도 행은 남는다. 사용자가
-- 직접 담은 목록이라 서버가 지우면 안 되고, 살 수 있는 상태인지는 조회할 때 판단한다.
--
-- 새 테이블만 만들므로 기존 행과 기능에 영향이 없다.
-- 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
CREATE TABLE consumer_wishlist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_model_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_consumer_wishlist PRIMARY KEY (id),
    -- 같은 상품을 두 번 담을 수 없다. 조회 후 삽입만으로는 동시 요청이 함께 통과한다.
    CONSTRAINT uk_consumer_wishlist_user_product UNIQUE (user_id, product_model_id),
    CONSTRAINT fk_consumer_wishlist_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_consumer_wishlist_product FOREIGN KEY (product_model_id)
        REFERENCES product_model (id),
    -- 목록은 user_id 로 좁혀 id 역순으로 읽는다. 유니크 키는 두 번째 열이 product_model_id
    -- 라 그 정렬을 받치지 못한다.
    INDEX idx_consumer_wishlist_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
