package com.ssafy.upcycle.user.geolocation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class CompanyGeolocationBackfillRunnerTests {

    @Test
    void fillsOnlyProfilesWhoseCoordinatesAreMissing() {
        CompanyProfileRepository profiles = mock(CompanyProfileRepository.class);
        AddressGeocoder geocoder = mock(AddressGeocoder.class);
        CompanyProfile profile = mock(CompanyProfile.class);
        GeoCoordinates coordinates = new GeoCoordinates(
                new BigDecimal("37.5665350"), new BigDecimal("126.9779692"));
        when(geocoder.isConfigured()).thenReturn(true);
        when(profiles.findAllByLatitudeIsNullOrLongitudeIsNull()).thenReturn(List.of(profile));
        when(profile.getAddress()).thenReturn("서울특별시 중구 세종대로 110");
        when(geocoder.geocode(profile.getAddress())).thenReturn(coordinates);

        runner(profiles, geocoder, true).run(new DefaultApplicationArguments());

        verify(profile).updateCoordinates(coordinates);
        verify(profiles).save(profile);
    }

    @Test
    void doesNothingUntilBackfillIsExplicitlyEnabled() {
        CompanyProfileRepository profiles = mock(CompanyProfileRepository.class);
        AddressGeocoder geocoder = mock(AddressGeocoder.class);

        runner(profiles, geocoder, false).run(new DefaultApplicationArguments());

        verifyNoInteractions(profiles, geocoder);
    }

    @Test
    void doesNotScanProfilesWithoutAConfiguredGeocoder() {
        CompanyProfileRepository profiles = mock(CompanyProfileRepository.class);
        AddressGeocoder geocoder = mock(AddressGeocoder.class);
        when(geocoder.isConfigured()).thenReturn(false);

        runner(profiles, geocoder, true).run(new DefaultApplicationArguments());

        verify(profiles, never()).findAllByLatitudeIsNullOrLongitudeIsNull();
    }

    private CompanyGeolocationBackfillRunner runner(
            CompanyProfileRepository profiles, AddressGeocoder geocoder, boolean enabled) {
        return new CompanyGeolocationBackfillRunner(
                profiles,
                geocoder,
                new KakaoGeocodingProperties("https://kakao.test", "key", enabled));
    }
}
