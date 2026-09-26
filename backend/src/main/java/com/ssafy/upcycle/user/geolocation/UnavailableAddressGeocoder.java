package com.ssafy.upcycle.user.geolocation;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyErrorCode;

/** REST API 키가 없을 때 좌표 없는 업체를 저장하지 않도록 명시적으로 실패한다. */
final class UnavailableAddressGeocoder implements AddressGeocoder {

    @Override
    public GeoCoordinates geocode(String address) {
        throw new BusinessException(CompanyErrorCode.COMPANY_GEOLOCATION_UNAVAILABLE);
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
