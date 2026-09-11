package com.ssafy.upcycle.user.geolocation;

/** 도로명·지번 주소를 지도에 표시할 WGS84 좌표로 변환한다. */
public interface AddressGeocoder {

    GeoCoordinates geocode(String address);

    default boolean isConfigured() {
        return true;
    }
}
