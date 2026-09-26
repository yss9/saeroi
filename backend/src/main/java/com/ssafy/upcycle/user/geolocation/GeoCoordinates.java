package com.ssafy.upcycle.user.geolocation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** WGS84 기준의 업체 위치 좌표. */
public record GeoCoordinates(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    public GeoCoordinates {
        requireRange(latitude, MIN_LATITUDE, MAX_LATITUDE, "latitude");
        requireRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "longitude");
        latitude = latitude.setScale(7, RoundingMode.HALF_UP);
        longitude = longitude.setScale(7, RoundingMode.HALF_UP);
    }

    private static void requireRange(
            BigDecimal value, BigDecimal minimum, BigDecimal maximum, String field) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
    }
}
