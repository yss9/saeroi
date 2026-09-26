-- Manual MySQL 8 migration. Apply once before deploying company geolocation.
--
-- 기존 업체는 아직 좌표가 없으므로 우선 NULL을 허용한다.
-- 배포 후 KAKAO_REST_API_KEY를 설정하고 COMPANY_GEOLOCATION_BACKFILL_ENABLED=true로
-- 한 번 기동하면 좌표가 없는 행만 변환한다. 실패한 행은 NULL로 남아
-- 다음 기동 시 재시도할 수 있다. 완료 후 플래그를 false로 돌린다.
ALTER TABLE company_profile
    ADD COLUMN latitude DECIMAL(10,7) NULL AFTER address_detail,
    ADD COLUMN longitude DECIMAL(10,7) NULL AFTER latitude,
    ADD CONSTRAINT chk_company_profile_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT chk_company_profile_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT chk_company_profile_coordinates_pair
        CHECK ((latitude IS NULL) = (longitude IS NULL));
