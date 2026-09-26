-- 기존 전역 소요일 구간을 각 등록 소재·공정 조합으로 복제한 뒤
-- 소재·공정별 소요일 구조로 교체한다. 이 프로젝트는 마이그레이션을 자동 실행하지 않는다.
CREATE TABLE processor_lead_time_v2 (
    id BIGINT NOT NULL AUTO_INCREMENT,
    processor_profile_id BIGINT NOT NULL,
    material_type VARCHAR(50) NOT NULL,
    process_type ENUM(
        'CLEANING', 'DISASSEMBLY', 'SORTING', 'CUTTING',
        'SHREDDING', 'COLOR_PROCESSING', 'SURFACE_FINISHING', 'REMANUFACTURING'
    ) NOT NULL,
    max_quantity BIGINT NOT NULL,
    lead_days INT NOT NULL,
    CONSTRAINT pk_processor_lead_time_v2 PRIMARY KEY (id),
    CONSTRAINT fk_processor_lead_time_v2_profile FOREIGN KEY (processor_profile_id)
        REFERENCES processor_profile (user_id),
    CONSTRAINT uk_processor_lead_time_v2 UNIQUE (
        processor_profile_id, material_type, process_type, max_quantity
    ),
    CONSTRAINT chk_processor_lead_time_v2_values CHECK (max_quantity > 0 AND lead_days > 0),
    INDEX idx_processor_lead_time_v2_lookup (
        processor_profile_id, material_type, process_type, max_quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO processor_lead_time_v2 (
    processor_profile_id, material_type, process_type, max_quantity, lead_days
)
SELECT lead_time.processor_profile_id,
       capability.material_type,
       capability.process_type,
       lead_time.max_quantity,
       lead_time.lead_days
FROM processor_lead_time lead_time
JOIN processor_capability capability
  ON capability.processor_profile_id = lead_time.processor_profile_id;

DROP TABLE processor_lead_time;
RENAME TABLE processor_lead_time_v2 TO processor_lead_time;
