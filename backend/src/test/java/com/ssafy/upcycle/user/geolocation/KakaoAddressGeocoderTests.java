package com.ssafy.upcycle.user.geolocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoAddressGeocoderTests {

    private static final String BASE_URL = "https://kakao-geocoding.test";
    private static final String REST_API_KEY = "test-rest-api-key";

    private MockRestServiceServer server;
    private KakaoAddressGeocoder geocoder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        geocoder = new KakaoAddressGeocoder(
                new KakaoGeocodingProperties(BASE_URL, REST_API_KEY, false), builder);
    }

    @Test
    void convertsKakaoXAndYToLongitudeAndLatitude() {
        server.expect(once(), requestTo(BASE_URL
                        + "/v2/local/search/address.json?query=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C%20%EC%A4%91%EA%B5%AC%20%EC%84%B8%EC%A2%85%EB%8C%80%EB%A1%9C%20110"))
                .andExpect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.GET))
                .andExpect(header("Authorization", "KakaoAK " + REST_API_KEY))
                .andRespond(withSuccess("""
                        {
                          "documents": [
                            {"x": "126.9779692", "y": "37.5665350"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoCoordinates result = geocoder.geocode("서울특별시 중구 세종대로 110");

        assertThat(result.latitude()).isEqualByComparingTo(new BigDecimal("37.5665350"));
        assertThat(result.longitude()).isEqualByComparingTo(new BigDecimal("126.9779692"));
        server.verify();
    }

    @Test
    void rejectsAnAddressWithNoSearchResult() {
        server.expect(requestTo(BASE_URL + "/v2/local/search/address.json?query=missing"))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geocoder.geocode("missing"))
                .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(CompanyErrorCode.COMPANY_ADDRESS_NOT_FOUND));
    }

    @Test
    void hidesGatewayFailureBehindTheCompanyErrorContract() {
        server.expect(requestTo(BASE_URL + "/v2/local/search/address.json?query=address"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> geocoder.geocode("address"))
                .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(CompanyErrorCode.COMPANY_GEOLOCATION_UNAVAILABLE));
    }
}
