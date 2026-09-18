-- 소재 종류(MaterialType)를 실제 취급 섬유 7종 + 기타로 좁힌 뒤 남은 기존 데이터를 옮긴다.
--
-- 왜 필요한가
-- ─────────────────────────────────────────────────────────────────────
-- MaterialType 이 DENIM, COTTON, LEATHER, LINING, METAL, MIXED, ETC 에서
-- COTTON, COTTON_MIXED, DENIM, POLYESTER, SILK, VISCOSE, WOOL, ETC 로 바뀌었다
-- (44faeec). material_type 은 DB ENUM 이 아니라 VARCHAR(50) 이라 스키마 변경은
-- 없었지만, 이미 저장된 LEATHER/LINING/METAL/MIXED 값은 새 enum에 없는 상수라
-- JPA 가 그 행을 읽을 때 IllegalArgumentException 으로 죽는다.
--
-- 어디로 옮기나
-- ─────────────────────────────────────────────────────────────────────
-- LEATHER, LINING, METAL 은 새 7종 섬유 어디에도 해당하지 않는 값이라 ETC 로 보낸다.
-- MIXED("혼방")는 COTTON_MIXED 로 보낸다 — 새 enum에서 가장 가까운 값이다.
-- COTTON, DENIM 은 그대로 남는다.
UPDATE material
SET material_type = 'ETC'
WHERE material_type IN ('LEATHER', 'LINING', 'METAL');

UPDATE material
SET material_type = 'COTTON_MIXED'
WHERE material_type = 'MIXED';

UPDATE processing_output
SET material_type = 'ETC'
WHERE material_type IN ('LEATHER', 'LINING', 'METAL');

UPDATE processing_output
SET material_type = 'COTTON_MIXED'
WHERE material_type = 'MIXED';

UPDATE processor_lead_time
SET material_type = 'ETC'
WHERE material_type IN ('LEATHER', 'LINING', 'METAL');

UPDATE processor_lead_time
SET material_type = 'COTTON_MIXED'
WHERE material_type = 'MIXED';

UPDATE processor_capability
SET material_type = 'ETC'
WHERE material_type IN ('LEATHER', 'LINING', 'METAL');

UPDATE processor_capability
SET material_type = 'COTTON_MIXED'
WHERE material_type = 'MIXED';
