package com.ssafy.upcycle.user.geolocation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 카카오맵 주소 검색 API로 업체 주소를 WGS84 좌표로 변환한다. */
final class KakaoAddressGeocoder implements AddressGeocoder {

    private static final Logger log = LoggerFactory.getLogger(KakaoAddressGeocoder.class);
    private static final String SEARCH_PATH = "/v2/local/search/address.json";

    private final RestClient restClient;

    KakaoAddressGeocoder(KakaoGeocodingProperties properties, RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                .build();
    }

    @Override
    public GeoCoordinates geocode(String address) {
        try {
            KakaoAddressResponse response = restClient.get()
                    .uri(uri -> uri.path(SEARCH_PATH).queryParam("query", address).build())
                    .retrieve()
                    .body(KakaoAddressResponse.class);
            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                throw new BusinessException(CompanyErrorCode.COMPANY_ADDRESS_NOT_FOUND);
            }

            KakaoAddressDocument first = response.documents().getFirst();
            return new GeoCoordinates(new BigDecimal(first.y()), new BigDecimal(first.x()));
        } catch (BusinessException expected) {
            throw expected;
        } catch (HttpStatusCodeException rejected) {
            log.error("Kakao geocoding rejected: status={}", rejected.getStatusCode());
            throw new BusinessException(CompanyErrorCode.COMPANY_GEOLOCATION_UNAVAILABLE);
        } catch (RestClientException | NumberFormatException | NullPointerException failure) {
            log.error("Kakao geocoding failed: {}", failure.getMessage());
            throw new BusinessException(CompanyErrorCode.COMPANY_GEOLOCATION_UNAVAILABLE);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAddressResponse(List<KakaoAddressDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAddressDocument(String x, String y) {
    }
}
