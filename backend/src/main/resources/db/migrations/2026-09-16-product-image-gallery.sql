-- 상품 상세 갤러리를 위한 product_image 테이블.
--
-- product_model.thumbnail_url 은 그대로 둔다. 그 값은 목록 카드가 쓰는 한 장이고 주문
-- 이력까지 따라다니는 반면, 이 테이블의 행들은 상세 화면이 넘겨 보는 묶음이다. 대표
-- 사진을 이쪽으로 옮기면 갤러리가 비어 있는 기존 제품은 목록에 그릴 사진이 없어진다.
--
-- 새 테이블만 만들므로 기존 행과 브랜드 관리 기능에 영향이 없다. 갤러리가 비어 있는
-- 제품의 공개 상세는 images: [] 로 나가고 화면은 thumbnailUrl 한 장으로 물러선다.
-- 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
CREATE TABLE product_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_model_id BIGINT NOT NULL,
    -- FileStorageService 가 돌려준 저장 키. 화면에 붙일 주소가 아니다.
    storage_key VARCHAR(500) NOT NULL,
    image_type ENUM('MAIN', 'ANGLE', 'DETAIL', 'CONTEXT') NOT NULL,
    -- 갤러리 안에서의 자리. 0 부터 빈틈없이 이어진다.
    sort_order INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_image PRIMARY KEY (id),
    CONSTRAINT uk_product_image_model_order UNIQUE (product_model_id, sort_order),
    CONSTRAINT fk_product_image_model FOREIGN KEY (product_model_id) REFERENCES product_model (id),
    CONSTRAINT chk_product_image_sort_order CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
