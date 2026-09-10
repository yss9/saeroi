-- Material event journal for an existing database.
CREATE TABLE IF NOT EXISTS material_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    actor_id BIGINT NULL,
    reference_type VARCHAR(40) NULL,
    reference_id BIGINT NULL,
    related_material_id BIGINT NULL,
    quantity BIGINT NULL,
    detail VARCHAR(500) NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    occurred_at DATETIME NOT NULL,
    CONSTRAINT pk_material_event PRIMARY KEY (id),
    CONSTRAINT uk_material_event_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_material_event_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_event_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_material_event_related_material
        FOREIGN KEY (related_material_id) REFERENCES material (id),
    CONSTRAINT chk_material_event_quantity CHECK (quantity IS NULL OR quantity >= 0),
    CONSTRAINT chk_material_event_reference CHECK (
        (reference_type IS NULL AND reference_id IS NULL)
        OR (reference_type IS NOT NULL AND reference_id IS NOT NULL)
    ),
    INDEX idx_material_event_timeline (material_id, occurred_at, id),
    INDEX idx_material_event_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 정확한 과거 사건을 추측하지 않는다. 도입 시점의 기준선만 남긴다.
INSERT IGNORE INTO material_event (
    material_id,
    event_type,
    actor_id,
    reference_type,
    reference_id,
    quantity,
    detail,
    idempotency_key,
    occurred_at
)
SELECT
    m.id,
    'MATERIAL_HISTORY_IMPORTED',
    m.registrant_id,
    'MATERIAL',
    m.id,
    m.total_quantity,
    '이벤트 저널 도입 전 생성된 소재의 기준 상태',
    CONCAT('MATERIAL_HISTORY_IMPORTED:', m.id),
    CURRENT_TIMESTAMP
FROM material m;
