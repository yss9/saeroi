package com.ssafy.upcycle.user.geolocation;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 좌표 컬럼 추가 전에 등록된 업체를 위한 일회성 backfill.
 *
 * <p>{@code app.geocoding.kakao.backfill-enabled=true}일 때만 실행한다. 변환에
 * 실패한 행은 {@code null}로 남겨 다음 실행에서 재시도하고, 성공한 행만 저장한다.
 */
@Component
public class CompanyGeolocationBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyGeolocationBackfillRunner.class);

    private final CompanyProfileRepository profiles;
    private final AddressGeocoder geocoder;
    private final KakaoGeocodingProperties properties;

    public CompanyGeolocationBackfillRunner(
            CompanyProfileRepository profiles,
            AddressGeocoder geocoder,
            KakaoGeocodingProperties properties) {
        this.profiles = profiles;
        this.geocoder = geocoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.backfillEnabled()) {
            return;
        }
        if (!geocoder.isConfigured()) {
            log.error("Company geolocation backfill requested but Kakao REST API key is missing");
            return;
        }

        int updated = 0;
        int failed = 0;
        for (CompanyProfile profile : profiles.findAllByLatitudeIsNullOrLongitudeIsNull()) {
            try {
                profile.updateCoordinates(geocoder.geocode(profile.getAddress()));
                profiles.save(profile);
                updated++;
            } catch (BusinessException failure) {
                failed++;
                log.warn("Company geolocation backfill failed for userId={}: {}",
                        profile.getUserId(), failure.getErrorCode().code());
            }
        }
        log.info("Company geolocation backfill finished: updated={}, failed={}", updated, failed);
    }
}
