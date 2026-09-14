package com.ssafy.upcycle.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.repository.WalletNonceRepository;
import com.ssafy.upcycle.user.geolocation.AddressGeocoder;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * S15P21E104-293 — <b>{@code docs/superpowers/specs/2026-09-08-role-permission-matrix.md} §3 표를
 * 데이터로 옮긴 테스트다.</b> 역할 5종 × 실제 엔드포인트의 허용·거부를 표 그대로 확인한다.
 *
 * <h2>왜 이 파일이 따로 있는가 — 기존 두 파일이 덮지 못하는 축</h2>
 *
 * <ul>
 *   <li>{@code RoleAuthorizationIntegrationTests} — 클레임이 <b>권한으로 바뀌는 배선</b>을 잰다.
 *       그래서 시험용 컨트롤러({@code /test/roles/*})를 쓴다. 실제 엔드포인트는 하나
 *       ({@code POST /api/materials})만 등장하고, 그것도 "DB 가 토큰을 이긴다" 를 보이기
 *       위한 것이다. <b>어느 엔드포인트가 어느 역할에게 열려 있는가는 재지 않는다.</b></li>
 *   <li>{@code RoleJourneyTests} — 역할별로 <b>자기 여정을 끝까지 걸을 수 있는가</b>를 잰다.
 *       세로(한 역할의 성공 경로)로 깊고, 가로(한 엔드포인트 × 다섯 역할)로는 얇다.
 *       거부 확인도 6건뿐이고 그중 셋은 {@code is4xxClientError()}·{@code isForbidden()} 으로
 *       뭉뚱그려져 <b>어느 {@code code} 가 나가는지가 고정돼 있지 않다.</b></li>
 * </ul>
 *
 * <p>이 파일은 <b>가로</b>다. 표의 한 행(엔드포인트)마다 다섯 역할을 모두 통과시켜, 열려야 할
 * 칸이 닫혀 있거나 닫혀야 할 칸이 열려 있으면 드러나게 한다. 성공 여정은 다시 걷지 않는다 —
 * 그것은 위 두 파일의 몫이다.
 *
 * <h2>거부가 403 인 칸과 도메인 에러 코드인 칸을 구분한다</h2>
 *
 * <p>Matrix §1 의 결정에 따라 인가의 정본은 <b>서비스 계층의 DB 재조회</b>이고,
 * {@code SecurityConfig} 에는 역할 규칙이 없다. 그래서 역할 거부는 대부분 일반 403 이 아니라
 * {@code MATERIAL_ROLE_NOT_ALLOWED} 같은 <b>도메인 에러 코드</b>로 나간다.
 * {@code @PreAuthorize} 는 저장소 전체에서 {@code TradeController} 의 발송·수령 두 곳뿐이고,
 * 그 둘만 일반 {@code FORBIDDEN} 을 낸다.
 *
 * <p><b>이 차이가 프론트와의 계약이다</b> — 화면이 {@code code} 문자열로 분기한다
 * ({@code TradeErrorCode} javadoc). 그래서 여기서는 어느 칸도 "4xx" 로 넘기지 않고
 * {@link Expectation#denied} 로 <b>상태와 {@code code} 를 같이</b> 못박는다.
 * 두 {@code @PreAuthorize} 칸이 일반 {@code FORBIDDEN} 을 내는 것과, 나머지가 구체 코드를
 * 내는 것을 같은 표에서 대비시키는 것이
 * {@link #preAuthorizeGatesReturnTheGenericForbiddenCode}·
 * {@link #serviceLayerDenialsCarryTheirOwnDomainCode} 두 테스트다.
 *
 * <h2>허용 칸을 200 으로 재지 않는 이유</h2>
 *
 * <p>허용 칸은 {@link Expectation#passesRoleGate()} 로 "역할 문을 통과했다" 까지만 잰다 —
 * 401 이 아니고 {@code code} 가 역할 거부 코드가 아니라는 것이다. 200 까지 요구하면 소재
 * 보유량·거래 상태·업로드된 이미지 같은 <b>도메인 선행조건</b>을 엔드포인트마다 만들어야
 * 하고, 그러면 도메인 규칙이 바뀔 때 인가 테스트가 같이 깨진다. 무엇이 깨졌는지 흐려지는
 * 것을 피하는 것이 {@code RoleAuthorizationIntegrationTests} 가 시험용 컨트롤러를 쓰는 이유와
 * 같다. 허용 칸이 <b>실제로 끝까지 동작하는지</b>는 {@code RoleJourneyTests} 가 이미 본다.
 *
 * <p>다만 <b>조회 전용 {@code AUTH} 칸은 200 을 요구한다</b>
 * ({@link #authOnlyReadsAreOpenToEveryRole}). Matrix §6-4 가 "CONSUMER 에게도 열어 둔다" 로
 * 결정한 것이 그 칸들이고, 그 결정의 내용이 곧 200 이기 때문이다.
 *
 * <h2>일부러 덮지 않은 것</h2>
 *
 * <ul>
 *   <li><b>{@code /admin/*} — Matrix §6-6 보류.</b> ADMIN 관리 엔드포인트의 권한 범위가
 *       결정되지 않았고 엔드포인트도 없다. 여기서 무엇을 할 수 있어야 하는지 테스트로
 *       정하면, 엔드포인트가 생길 때 그 표가 이미 "정해진 것" 으로 읽혀 재검토를 막는다.
 *       ADMIN 은 표에 있는 칸에서만 확인한다 — §4 대로 허용 칸이 하나도 없다.</li>
 *   <li><b>비회원 QR 공개 조회 — §6-3 결정·후속 티켓.</b> {@code GET /qr/{qrToken}} 이 아직
 *       없다. 여기서는 {@code /api/materials/**} 가 지금도 인증을 요구하는 것만 고정한다.</li>
 *   <li><b>Matrix §5 의 갭(발송·수령이 DB 를 다시 읽지 않는다)</b> — {@code trade} 담당자에게
 *       넘어간 미해결 항목이다. 정지 계정을 만들어 실패하는 테스트를 남기면 남의 영역의
 *       미구현을 이 브랜치의 빨간 테스트로 옮기는 것이 된다.</li>
 *   <li><b>표에 없는 엔드포인트</b> — {@code /chain} 계열, 제품 이미지 업로드 등. §7-2 의
 *       원칙대로 표가 다루지 않는 칸을 상상해 채우지 않는다.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Role × 엔드포인트 권한 Matrix")
class RoleApiAccessMatrixTests {

    private static final String PASSWORD = "Matrix1234!";
    private static final long CHAIN_ID = 31221L;

    /** 표에 없는 id. 역할 검사가 조회보다 <b>먼저</b> 오는 것을 이용해 상태를 바꾸지 않고 문만 두드린다. */
    private static final long ABSENT_ID = 999_999L;

    /**
     * 역할 문이 낸 거부 코드 전부.
     *
     * <p>허용 칸의 단언이 이 집합의 여집합이다. 새 역할 코드가 생기면 여기에 추가해야
     * 허용 칸이 계속 유효하다 — 빠뜨리면 <b>거부가 통과로 집계된다.</b>
     */
    private static final Set<String> ROLE_DENIAL_CODES = Set.of(
            "FORBIDDEN",
            "UNAUTHORIZED",
            "MATERIAL_ROLE_NOT_ALLOWED",
            "LISTING_ROLE_NOT_ALLOWED",
            "TRADE_ROLE_NOT_ALLOWED",
            "PRODUCT_ROLE_NOT_ALLOWED",
            "WALLET_ROLE_NOT_ALLOWED",
            "PROCESSING_FORBIDDEN");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AddressGeocoder addressGeocoder;

    @Autowired private UserRepository userRepository;
    @Autowired private UserWalletRepository walletRepository;
    @Autowired private WalletNonceRepository walletNonceRepository;
    @Autowired private CompanyProfileRepository companyProfileRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialBalanceRepository balanceRepository;
    @Autowired private MaterialLockRepository lockRepository;
    @Autowired private MaterialListingRepository listingRepository;
    @Autowired private MaterialTradeRepository tradeRepository;
    @Autowired private TradeShippingRepository shippingRepository;
    @Autowired private EvidenceFileRepository evidenceFileRepository;

    private final Map<UserRole, String> tokens = new EnumMap<>(UserRole.class);
    private long materialId;
    private long listingId;
    private long tradeId;

    /**
     * 다섯 역할의 토큰과 표를 두드릴 최소 데이터를 <b>한 번만</b> 만든다.
     *
     * <p>케이스가 200여 개라 매 케이스마다 다시 만들면 실행 시간이 그만큼 곱해진다.
     * 대신 <b>어떤 케이스도 이 데이터를 바꾸지 않게</b> 표를 짰다 — 거부 케이스는 상태를
     * 바꾸지 못하고, 허용 케이스는 존재하지 않는 id 를 쓰거나 새 행만 더한다.
     * 성공 경로를 걸어 상태를 전이시키는 것은 {@code RoleJourneyTests} 의 몫이다.
     *
     * <p>토큰은 <b>실제 로그인</b>으로 받는다. {@code jwtTokenService} 를 직접 부르면
     * 로그인이 역할을 넣는 것을 멈춰도 이 파일은 계속 통과한다.
     */
    @BeforeAll
    void seed() throws Exception {
        clean();
        when(addressGeocoder.geocode(anyString())).thenReturn(new GeoCoordinates(
                new BigDecimal("37.5012345"), new BigDecimal("127.0123456")));
        tokens.put(UserRole.SUPPLIER, signUpBusinessAndLogin(UserRole.SUPPLIER,
                "0xaaaa000000000000000000000000000000000001"));
        tokens.put(UserRole.BRAND, signUpBusinessAndLogin(UserRole.BRAND,
                "0xbbbb000000000000000000000000000000000002"));
        tokens.put(UserRole.PROCESSOR, signUpBusinessAndLogin(UserRole.PROCESSOR,
                "0xcccc000000000000000000000000000000000003"));
        tokens.put(UserRole.CONSUMER, signUpConsumerAndLogin());
        tokens.put(UserRole.ADMIN, createAdminDirectlyAndLogin());

        materialId = registerMaterial();
        listingId = openListing();
        tradeId = buyPartOfTheListing();
    }

    @AfterAll
    void cleanUp() {
        // @SpringBootTest 는 롤백하지 않는다. 커밋된 행이 같은 인메모리 H2 를 쓰는 다른
        // 테스트로 새면 단독 실행만 통과하는 실패가 된다.
        clean();
    }

    // ── §3-1 공통 · 인증 ─────────────────────────────────────

    /**
     * {@code PUBLIC} 칸은 토큰 없이 닿아야 한다.
     *
     * <p>{@code SecurityConfigTests} 는 {@code /api/v1/auth/test}(가상의 경로) 하나로
     * 프리픽스가 열려 있음을 고정한다. 여기서는 <b>표에 적힌 실제 다섯 경로 + 헬스</b>가
     * 열려 있는지를 본다 — Matrix §6-7 이 "와일드카드를 좁히지 않는다" 로 결정한 대신
     * 계약으로 삼은 것이 이 목록이고, 좁히려는 변경이 들어오면 여기서 먼저 깨진다.
     *
     * <p>200 을 요구하지 않는다. 빈 바디의 가입 요청은 400 이 맞고, 여기서 재는 것은
     * <b>필터 체인이 컨트롤러에 닿게 해주는가</b> 하나다.
     */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("publicEndpoints")
    @DisplayName("§3-1 PUBLIC 칸은 토큰 없이 컨트롤러에 닿는다")
    void publicEndpointsNeedNoToken(String method, String path) throws Exception {
        mockMvc.perform(requestFor(method, path, null))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("%s %s 는 PUBLIC 이어야 한다", method, path)
                        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> publicEndpoints() {
        return Stream.of(
                args("GET", "/api/health"),
                args("POST", "/api/v1/auth/signup/consumer"),
                args("POST", "/api/v1/auth/signup/business"),
                args("POST", "/api/v1/auth/login"),
                args("POST", "/api/v1/auth/wallet/nonce"),
                args("POST", "/api/v1/auth/wallet/verify"));
    }

    /**
     * {@code ADMIN} 은 가입 API 로 만들 수 없다 — §3-1 · §4 의 근거 중 하나다.
     *
     * <p>{@code SignupService} 가 {@code canOwnWallet()} 로 막으므로 {@code CONSUMER} 도
     * 이 경로로는 만들 수 없다({@code /signup/consumer} 가 따로 있다). 그래서 이 테스트가
     * 고정하는 것은 <b>사업자 가입이 열어 주는 역할이 정확히 셋</b>이라는 것이다.
     * ADMIN 계정이 DB 에만 존재한다는 사실이 §4 가 ADMIN 을 만능으로 두지 않은 근거이므로,
     * 그 사실 자체를 테스트로 잡아 둔다.
     */
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONSUMER", "ADMIN"})
    @DisplayName("§3-1 사업자 가입은 지갑을 가질 수 있는 세 역할만 만든다")
    void businessSignupRefusesConsumerAndAdmin(UserRole role) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/signup/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(businessSignupBody("refused-" + role.name() + "@test", role))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_ROLE"));
    }

    // ── 인증 경계 ────────────────────────────────────────────

    /**
     * {@code PUBLIC} 이 아닌 모든 칸은 토큰이 없으면 <b>401 + 공통 봉투</b>다.
     *
     * <p>{@code SecurityConfigTests} 와 {@code RoleAuthorizationIntegrationTests} 는 각각
     * 경로 하나로 이 형태를 고정한다. 표에는 40여 행이 있고 그중 하나만 실수로 permitAll 에
     * 들어가도 두 테스트는 통과한다 — 특히 {@code processing} 의 <b>무프리픽스 별칭</b>은
     * {@code /api} 로 시작하지도 않아 URL 규칙을 손댈 때 가장 먼저 새는 자리다(§1).
     *
     * <p>401 과 403 을 구분하는 것이 계약이다. 둘을 같은 응답으로 내면 화면이 "다시 로그인" 과
     * "권한이 없음" 을 구별할 수 없다.
     */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedEndpoints")
    @DisplayName("PUBLIC 이 아닌 칸은 토큰 없이는 401 + 공통 봉투")
    void protectedEndpointsRejectAMissingToken(String method, String path) throws Exception {
        mockMvc.perform(requestFor(method, resolve(path), null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> protectedEndpoints() {
        return Stream.of(
                // §3-2 지갑
                args("POST", "/api/v1/wallets/nonce"),
                args("POST", "/api/v1/wallets"),
                args("GET", "/api/v1/wallets/me"),
                args("DELETE", "/api/v1/wallets/" + ABSENT_ID),
                // 소비자 배송지 — 로그인 없이는 단 한 줄도 보여서는 안 된다(남의 집 주소다)
                // 제품 주문 — 구매자는 JWT 에서만 얻는다. 로그인 없이 주문이 들어오면
                // 그 주문은 주인이 없다.
                args("POST", "/api/v1/product-orders"),
                args("POST", "/api/v1/product-orders/" + ABSENT_ID + "/payment/ready"),
                args("POST", "/api/v1/product-orders/" + ABSENT_ID + "/payment/approve"),
                args("POST", "/api/v1/product-orders/" + ABSENT_ID + "/cancel"),
                args("GET", "/api/v1/consumers/me/addresses"),
                args("POST", "/api/v1/consumers/me/addresses"),
                args("PUT", "/api/v1/consumers/me/addresses/" + ABSENT_ID),
                args("DELETE", "/api/v1/consumers/me/addresses/" + ABSENT_ID),
                args("PATCH", "/api/v1/consumers/me/addresses/" + ABSENT_ID + "/default"),
                // §3-3 소재
                args("POST", "/api/materials"),
                args("GET", "/api/materials/me"),
                args("GET", "/api/materials/{material}"),
                args("GET", "/api/materials/{material}/balance"),
                args("GET", "/api/materials/{material}/lineage"),
                // §3-4 판매 글
                args("POST", "/api/v1/listings"),
                args("GET", "/api/v1/listings"),
                args("GET", "/api/v1/listings/{listing}"),
                args("DELETE", "/api/v1/listings/{listing}"),
                // §3-5 거래
                args("GET", "/api/v1/trades/quote"),
                args("POST", "/api/v1/trades"),
                args("GET", "/api/v1/trades"),
                args("GET", "/api/v1/trades/{trade}"),
                args("POST", "/api/v1/trades/{trade}/payment/ready"),
                args("POST", "/api/v1/trades/{trade}/payment/approve"),
                args("POST", "/api/v1/trades/{trade}/cancel"),
                args("PATCH", "/api/v1/trades/{trade}/ship"),
                args("POST", "/api/v1/trades/{trade}/receive/confirm"),
                // §3-6 processing — /api/v1 경로와 무프리픽스 별칭을 <b>둘 다</b> 본다
                args("GET", "/api/v1/processing-requests"),
                args("GET", "/processing-requests"),
                args("POST", "/api/v1/processing-requests"),
                args("POST", "/processing-requests"),
                args("GET", "/api/v1/processing-requests/" + ABSENT_ID),
                args("POST", "/api/v1/processing-requests/" + ABSENT_ID + "/cancel"),
                args("POST", "/api/v1/processing-requests/" + ABSENT_ID + "/reject"),
                args("POST", "/api/v1/processing-requests/" + ABSENT_ID + "/quotes"),
                args("GET", "/api/v1/processing-requests/" + ABSENT_ID + "/quotes"),
                args("POST", "/api/v1/quotes/" + ABSENT_ID + "/select"),
                args("POST", "/quotes/" + ABSENT_ID + "/select"),
                args("GET", "/api/v1/processing-jobs/" + ABSENT_ID),
                args("GET", "/processing-jobs/" + ABSENT_ID),
                args("POST", "/api/v1/processing-jobs/" + ABSENT_ID + "/payment"),
                args("POST", "/api/v1/processing-jobs/" + ABSENT_ID + "/payment/confirm"),
                args("GET", "/api/v1/processors/search"),
                args("GET", "/processors/search"),
                args("GET", "/api/v1/processing-requests/materials"),
                args("GET", "/processing-requests/materials"),
                // §3-7 제품 모델
                args("POST", "/api/v1/product-models"),
                args("PATCH", "/api/v1/product-models/" + ABSENT_ID + "/status"),
                // §3-8 증빙
                args("GET", "/api/evidence"),
                args("DELETE", "/api/evidence/" + ABSENT_ID));
    }

    // ── 표 본문 ──────────────────────────────────────────────

    /**
     * <b>서비스 계층이 막는 칸</b> — 거부가 구체 도메인 코드로 나간다.
     *
     * <p>표의 "강제" 열이 서비스 이름을 적어 둔 칸들이다. 각 엔드포인트마다 다섯 역할을 모두
     * 통과시켜, 허용 목록이 조용히 넓어지거나 좁아지면 드러나게 한다.
     *
     * <p>이 표가 고정하는 결정들:
     *
     * <ul>
     *   <li>§3-3 {@code POST /api/materials} 에서 <b>PROCESSOR 가 빠진다.</b> 가공업체의
     *       소재는 가공 확정의 산출물이지 등록 대상이 아니다</li>
     *   <li>§3-4 {@code POST /api/v1/listings} 에서 <b>BRAND 가 빠진다.</b> 소재를 등록하는
     *       것과 파는 것은 다른 권한이다 ({@code canRegisterMaterial()} 을 쓰지 않는 이유).
     *       레퍼런스 명세와 어긋나며 §7-1 이 구현을 따르기로 했다</li>
     *   <li>§3-5 {@code POST /api/v1/trades} 는 <b>BRAND 만</b> — §6-5 결정(현재 유지)</li>
     *   <li>§3-7 {@code POST /api/v1/product-models} 는 <b>BRAND 만</b>.
     *       {@code canRegisterProduct()} 와 {@code canRegisterMaterial()} 의 목록이 다른 것을
     *       합치면 SUPPLIER 가 제품을 올릴 수 있게 조용히 넓어진다</li>
     *   <li>§3-2 지갑 등록은 <b>{@code canOwnWallet()} 세 역할</b>. CONSUMER 는 지갑 없이
     *       서비스를 쓰고 ADMIN 은 온체인 작업 주체가 아니다</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceLayerRoleGates")
    @DisplayName("§3 서비스 계층 거부는 각자의 도메인 에러 코드로 나간다")
    void serviceLayerDenialsCarryTheirOwnDomainCode(Case testCase) throws Exception {
        assertCase(testCase);
    }

    private static Stream<Case> serviceLayerRoleGates() {
        List<Case> cases = new ArrayList<>();
        // §3-2 지갑 — WalletRegistrationService#requireWalletCapableUser
        cases.addAll(gate("POST", "/api/v1/wallets/nonce",
                Set.of(UserRole.SUPPLIER, UserRole.BRAND, UserRole.PROCESSOR),
                HttpStatus.FORBIDDEN, "WALLET_ROLE_NOT_ALLOWED"));
        cases.addAll(gate("POST", "/api/v1/wallets",
                Set.of(UserRole.SUPPLIER, UserRole.BRAND, UserRole.PROCESSOR),
                HttpStatus.FORBIDDEN, "WALLET_ROLE_NOT_ALLOWED"));
        // §3-3 소재 — MaterialRegistrationService#requireMaterialCapableUser
        cases.addAll(gate("POST", "/api/materials",
                Set.of(UserRole.SUPPLIER, UserRole.BRAND),
                HttpStatus.FORBIDDEN, "MATERIAL_ROLE_NOT_ALLOWED"));
        // §3-4 판매 글 — ListingCommandService#requireSellingCapableUser
        cases.addAll(gate("POST", "/api/v1/listings",
                Set.of(UserRole.SUPPLIER),
                HttpStatus.FORBIDDEN, "LISTING_ROLE_NOT_ALLOWED"));
        // §3-5 구매 — TradeCommandService#requireBuyingCapableUser
        cases.addAll(gate("POST", "/api/v1/trades",
                Set.of(UserRole.BRAND),
                HttpStatus.FORBIDDEN, "TRADE_ROLE_NOT_ALLOWED"));
        // §3-7 제품 모델 — ProductSellerPolicy#requireSeller
        cases.addAll(gate("POST", "/api/v1/product-models",
                Set.of(UserRole.BRAND),
                HttpStatus.FORBIDDEN, "PRODUCT_ROLE_NOT_ALLOWED"));
        return cases.stream();
    }

    /**
     * <b>{@code @PreAuthorize} 가 막는 칸 — 여기만 일반 {@code FORBIDDEN} 이다.</b>
     *
     * <p>저장소 전체에서 두 곳뿐이고, Matrix §1 은 그 둘을 <b>관례가 아니라 예외</b>로
     * 규정했다. 위 테스트와 이 테스트를 나란히 두는 이유가 그것이다 — 같은 "역할 거부" 인데
     * 나가는 {@code code} 가 다르고, 프론트는 그 문자열로 분기한다.
     *
     * <p>허용 역할이 {@code TRADE_NOT_FOUND} 를 받는 것이 단언의 절반이다. 문을 통과했다는
     * 증거이면서, <b>일반 {@code FORBIDDEN} 이 아니라는 것</b>을 같이 보인다. 존재하지 않는
     * 거래 id 를 쓰므로 발송·수령이 실제로 일어나지 않는다.
     *
     * <p>§3-5 의 근거대로 발송은 <b>판매 등록 자격(SUPPLIER)과 같은 목록</b>이고 수령 확인은
     * <b>구매 자격(BRAND)과 같은 목록</b>이어야 한다. 어긋나면 결제까지 됐는데 발송이 막히는
     * 거래가 생긴다. 두 목록이 갈라지면 이 표와 {@link #serviceLayerRoleGates} 가 함께 깨진다.
     *
     * <p>Matrix §5 는 이 두 곳이 <b>서비스에서 DB 를 다시 읽지 않는다</b>는 갭을 적어 두었고
     * {@code trade} 담당자에게 넘겼다. 이 테스트는 그 갭을 메우지 않는다 — 지금 있는 문이
     * 무엇을 내보내는지만 고정한다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("preAuthorizeGates")
    @DisplayName("§3-5 @PreAuthorize 두 칸만 일반 FORBIDDEN 을 낸다")
    void preAuthorizeGatesReturnTheGenericForbiddenCode(Case testCase) throws Exception {
        assertCase(testCase);
    }

    private static Stream<Case> preAuthorizeGates() {
        List<Case> cases = new ArrayList<>();
        cases.addAll(gate("PATCH", "/api/v1/trades/" + ABSENT_ID + "/ship",
                Set.of(UserRole.SUPPLIER), HttpStatus.FORBIDDEN, "FORBIDDEN",
                HttpStatus.NOT_FOUND, "TRADE_NOT_FOUND"));
        cases.addAll(gate("POST", "/api/v1/trades/" + ABSENT_ID + "/receive/confirm",
                Set.of(UserRole.BRAND), HttpStatus.FORBIDDEN, "FORBIDDEN",
                HttpStatus.NOT_FOUND, "TRADE_NOT_FOUND"));
        return cases.stream();
    }

    /**
     * <b>Processing (§3-6) — 다른 담당자 영역이다.</b> 표의 "강제" 열대로 전부
     * {@code PROCESSING_FORBIDDEN} 이며, 역할별로 <b>누가 통과하는지</b>만 다르다.
     *
     * <p>여기서 확인하는 것은 표의 칸이지 그 정책의 타당성이 아니다. 프로덕션 코드는 손대지
     * 않는다. 존재하지 않는 요청·견적·작업 id 를 쓰는데, 그래도 성립하는 이유는 이 서비스들이
     * <b>역할 검사를 조회보다 먼저</b> 하기 때문이다 — 허용 역할은 조회 단계까지 가서
     * {@code *_NOT_FOUND} 를 받고, 그것이 문을 통과했다는 증거가 된다.
     *
     * <p>{@code /api/v1} 경로와 <b>무프리픽스 별칭</b>을 같은 표에서 돈다. §1 이 URL 규칙에
     * 역할 조건을 넣지 않기로 한 직접적인 근거가 이 별칭이고, 두 경로가 같은 판정을 받는지는
     * 실제로 두 번 두드려 봐야 알 수 있다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("processingRoleGates")
    @DisplayName("§3-6 Processing 은 전부 PROCESSING_FORBIDDEN — 별칭 경로도 같다")
    void processingRoleGatesAreIdenticalOnBothPathAliases(Case testCase) throws Exception {
        assertCase(testCase);
    }

    private static Stream<Case> processingRoleGates() {
        List<Case> cases = new ArrayList<>();
        Set<UserRole> brand = Set.of(UserRole.BRAND);
        Set<UserRole> processor = Set.of(UserRole.PROCESSOR);
        record Row(String method, String path, Set<UserRole> allowed) {}
        List<Row> rows = List.of(
                new Row("POST", "/processing-requests", brand),
                new Row("POST", "/processing-requests/" + ABSENT_ID + "/cancel", brand),
                new Row("PATCH", "/processing-requests/" + ABSENT_ID + "/cancel", brand),
                new Row("POST", "/processing-requests/" + ABSENT_ID + "/reject", processor),
                new Row("POST", "/processing-requests/" + ABSENT_ID + "/quotes", processor),
                new Row("GET", "/processing-requests/" + ABSENT_ID + "/quotes", brand),
                new Row("POST", "/quotes/" + ABSENT_ID + "/select", brand),
                new Row("POST", "/processing-jobs/" + ABSENT_ID + "/payment", brand),
                new Row("POST", "/processing-jobs/" + ABSENT_ID + "/payment/confirm", brand),
                new Row("GET", "/processors/search", brand),
                new Row("GET", "/processing-requests/materials", brand));
        for (Row row : rows) {
            for (String prefix : List.of("", "/api/v1")) {
                cases.addAll(gate(row.method(), prefix + row.path(), row.allowed(),
                        HttpStatus.FORBIDDEN, "PROCESSING_FORBIDDEN"));
            }
        }
        return cases.stream();
    }

    /**
     * §6-4 결정 — <b>추적성 조회는 역할로 좁히지 않는다.</b>
     *
     * <p>{@code CONSUMER} 도 소재 정보를 본다. {@code ListingController} javadoc 의
     * "소재 시세와 재고는 사업자 간 거래 정보" 라는 서술은 <b>비회원에게 열지 않는 이유</b>로
     * 읽고, 역할로 좁히라는 지시로 읽지 않는다는 것이 그 결정이다.
     *
     * <p>이 칸들은 {@code anyRequest().authenticated()} 뿐이라 <b>지금은 코드가 이미 결정을
     * 만족한다.</b> 그래서 이 테스트는 새 기능을 확인하는 것이 아니라, 나중에 누군가
     * {@code SecurityConfig} 나 서비스에 역할 조건을 넣어 좁히는 것을 막는 <b>못</b>이다.
     * 여기만 200 을 요구하는 이유는 결정의 내용이 곧 "값이 보인다" 이기 때문이다.
     *
     * <p>{@code /materials/me}·{@code /wallets/me}·{@code /trades} 도 같은 표에 있다.
     * 표는 이들을 {@code ●소}/{@code AUTH} 로 적었는데 범위가 JWT {@code sub} 로 강제되므로
     * 막을 필요가 없고, CONSUMER·ADMIN 은 <b>빈 결과</b>를 받는다 — 403 이 아니다.
     */
    @ParameterizedTest(name = "{1} — {0}")
    @MethodSource("authOnlyReads")
    @DisplayName("§6-4 AUTH 조회 칸은 다섯 역할 모두 200")
    void authOnlyReadsAreOpenToEveryRole(UserRole role, String path) throws Exception {
        mockMvc.perform(requestFor("GET", resolve(path), tokens.get(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> authOnlyReads() {
        List<String> paths = List.of(
                "/api/materials/{material}",
                "/api/materials/{material}/balance",
                "/api/materials/{material}/lineage",
                "/api/materials/me",
                "/api/v1/listings",
                "/api/v1/listings/{listing}",
                "/api/v1/trades",
                "/api/v1/trades/quote?listingId={listing}&quantity=1&unit=KG",
                "/api/v1/wallets/me",
                // §3-6 목록은 역할로 scope 만 갈린다 — 그 외 역할은 `1=0`, 즉 빈 목록이지 403 이 아니다
                "/api/v1/processing-requests");
        List<org.junit.jupiter.params.provider.Arguments> cases = new ArrayList<>();
        for (String path : paths) {
            for (UserRole role : UserRole.values()) {
                cases.add(args(role, path));
            }
        }
        return cases.stream();
    }

    /**
     * <b>역할을 아예 보지 않는 칸.</b> 표의 "강제" 열이 소유권·당사자·관계를 적어 둔 자리다.
     *
     * <p>다섯 역할이 <b>같은</b> 상태와 {@code code} 를 받는 것이 단언이다. 하나만 확인하면
     * "역할로도 갈린다" 는 상태와 구별되지 않는다. 표가 이 칸들을 {@code ○} 로 적은 것은
     * 역할이 막기 때문이 아니라 <b>그 역할이 소유자·당사자가 될 수 없기 때문</b>이며,
     * 그 구분이 응답 {@code code} 에 그대로 드러난다.
     *
     * <ul>
     *   <li>§3-2 {@code DELETE /wallets/{id}} — 역할 검사가 없다. 지갑을 가질 수 없는 역할은
     *       애초에 지울 지갑이 없어 {@code WALLET_NOT_FOUND} 다</li>
     *   <li>§3-4 {@code DELETE /listings/{id}} — {@code LISTING_NOT_OWNED}.
     *       소유자(SUPPLIER)는 넣지 않는다. 넣으면 이 케이스가 판매 글을 실제로 내려
     *       뒤에 실행되는 다른 케이스의 전제를 바꾼다</li>
     *   <li>§3-5 당사자 검사 칸 — {@code TRADE_NOT_PARTICIPANT}. 당사자(판매자 SUPPLIER ·
     *       구매자 BRAND)의 성공 경로는 {@code RoleJourneyTests} 가 걷는다</li>
     *   <li>§3-7 {@code PATCH /product-models/{id}/status} — 역할 검사가 없고 등록자만
     *       대조한다. 역할만 보면 브랜드 아무나 남의 제품을 단종시킬 수 있다</li>
     *   <li>§3-8 증빙 — 열람 자격이 등록자·보유자 <b>관계</b>에서 나온다. 두 SUPPLIER 는
     *       서로의 증빙과 아무 상관이 없어 역할이 관계를 대신할 수 없다</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("ownershipGuardedEndpoints")
    @DisplayName("§3 소유권·당사자·관계로 막는 칸은 역할을 보지 않는다")
    void ownershipGuardedEndpointsAnswerTheSameToEveryRole(
            String method, String path, List<UserRole> roles, HttpStatus status, String code)
            throws Exception {
        for (UserRole role : roles) {
            mockMvc.perform(requestFor(method, resolve(path), tokens.get(role)))
                    .andExpect(status().is(status.value()))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> ownershipGuardedEndpoints() {
        List<UserRole> all = List.of(UserRole.values());
        List<UserRole> nonOwners = List.of(
                UserRole.BRAND, UserRole.PROCESSOR, UserRole.CONSUMER, UserRole.ADMIN);
        List<UserRole> nonParticipants = List.of(UserRole.PROCESSOR, UserRole.CONSUMER, UserRole.ADMIN);
        return Stream.of(
                args("DELETE", "/api/v1/wallets/" + ABSENT_ID, all,
                        HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND"),
                args("DELETE", "/api/v1/listings/{listing}", nonOwners,
                        HttpStatus.FORBIDDEN, "LISTING_NOT_OWNED"),
                args("GET", "/api/v1/trades/{trade}", nonParticipants,
                        HttpStatus.FORBIDDEN, "TRADE_NOT_PARTICIPANT"),
                // 판매자(SUPPLIER)도 구매자가 아니라 거부된다 — 청구 대상은 구매자다
                args("POST", "/api/v1/trades/{trade}/payment/ready",
                        List.of(UserRole.SUPPLIER, UserRole.PROCESSOR, UserRole.CONSUMER, UserRole.ADMIN),
                        HttpStatus.FORBIDDEN, "TRADE_NOT_PARTICIPANT"),
                args("POST", "/api/v1/trades/{trade}/payment/approve",
                        List.of(UserRole.SUPPLIER, UserRole.PROCESSOR, UserRole.CONSUMER, UserRole.ADMIN),
                        HttpStatus.FORBIDDEN, "TRADE_NOT_PARTICIPANT"),
                // 취소는 양쪽 모두 가능하므로 당사자 아닌 셋만 거부된다
                args("POST", "/api/v1/trades/{trade}/cancel", nonParticipants,
                        HttpStatus.FORBIDDEN, "TRADE_NOT_PARTICIPANT"),
                args("PATCH", "/api/v1/product-models/" + ABSENT_ID + "/status", all,
                        HttpStatus.NOT_FOUND, "PRODUCT_MODEL_NOT_FOUND"),
                args("DELETE", "/api/evidence/" + ABSENT_ID, all,
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND"));
    }

    /**
     * §3-6 의 당사자 검사 칸 — <b>다섯 역할이 같은 답을 받는다.</b>
     *
     * <p>{@code GET /processing-requests/{id}} 와 {@code GET /processing-jobs/{id}} 는 역할이
     * 아니라 당사자인지로 판정한다. 표가 BRA·PRO 를 {@code ●소} 로 적은 것은 <b>그 둘만
     * 당사자가 될 수 있기 때문</b>이지 역할 검사가 있기 때문이 아니다. 응답이 역할과
     * 무관하다는 것을 확인해 그 구분을 고정한다.
     *
     * <p>기대 코드를 하드코딩하지 않고 <b>다섯 응답이 서로 같은지</b>만 본다. 이 두
     * 엔드포인트는 다른 담당자 영역이고 표는 코드를 못박지 않았다 — 여기서 특정 코드를
     * 요구하면 표에 없는 계약을 이 테스트가 발명하는 것이 된다.
     */
    @ParameterizedTest(name = "GET {0}")
    @MethodSource("processingParticipantReads")
    @DisplayName("§3-6 당사자 검사 칸은 역할과 무관하게 같은 응답")
    void processingParticipantReadsDoNotVaryByRole(String path) throws Exception {
        Set<String> answers = new LinkedHashSet<>();
        for (UserRole role : UserRole.values()) {
            MvcResult result = mockMvc.perform(requestFor("GET", path, tokens.get(role))).andReturn();
            answers.add(result.getResponse().getStatus() + " "
                    + codeOf(result.getResponse().getContentAsString()));
        }
        assertThat(answers)
                .as("%s 의 응답이 역할에 따라 갈린다 — 표는 당사자 검사만 적고 있다", path)
                .hasSize(1);
    }

    private static Stream<String> processingParticipantReads() {
        return Stream.of(
                "/api/v1/processing-requests/" + ABSENT_ID,
                "/api/v1/processing-jobs/" + ABSENT_ID,
                "/processing-requests/" + ABSENT_ID,
                "/processing-jobs/" + ABSENT_ID);
    }

    /**
     * §3-8 · §6-1 결정 — 증빙 목록은 <b>보는 사람에 따라 응답 모양이 다르다.</b>
     *
     * <p>업로더 · 소재 등록자 · 현재 보유자는 {@code FULL}, 그 외 인증 사용자는
     * {@code SUMMARY} 다. 403 으로 막지 않은 이유는 "계근표 2건 있음" 이 추적성 정보이기
     * 때문이고, 파일명·해시·업로더까지 내보내지 않는 이유는 그것이 거래 정보이기 때문이다.
     *
     * <p><b>역할이 아니라 관계로 갈린다</b>는 것이 이 테스트의 요점이다. 소재를 등록한
     * SUPPLIER 만 {@code FULL} 이고, 나머지 네 역할은 사업자든 소비자든 관리자든 모두
     * {@code SUMMARY} 다 — {@code ADMIN} 이 여기서도 특별하지 않은 것이 §4 다.
     */
    @ParameterizedTest
    @EnumSource(UserRole.class)
    @DisplayName("§6-1 증빙 목록은 관계로 갈린다 — 역할이 아니다")
    void evidenceListVisibilityFollowsRelationshipNotRole(UserRole role) throws Exception {
        String expected = role == UserRole.SUPPLIER ? "FULL" : "SUMMARY";

        mockMvc.perform(requestFor("GET",
                        "/api/evidence?referenceType=MATERIAL&referenceId=" + materialId,
                        tokens.get(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value(expected));
    }

    /**
     * §4 — <b>{@code ADMIN} 은 표에서 단 한 칸도 {@code ●} 를 갖지 않는다.</b>
     *
     * <p>위 표들에 ADMIN 이 흩어져 있어 "한 칸도 없다" 는 진술 자체는 어디에도 없다. 이
     * 테스트가 그 진술이다 — 쓰기 엔드포인트를 모아 돌면서 ADMIN 이 전부 거부되는지 본다.
     * ADMIN 을 만능으로 여는 변경이 들어오면 여기서 한 번에 드러난다.
     *
     * <p>근거는 신뢰 모델이다. 이 시스템은 <b>당사자 서명</b>을 신뢰의 근거로 삼으므로,
     * ADMIN 이 남의 리소스를 대신 조작할 수 있으면 온체인 서명이 "누가 실제로 했는가" 를
     * 증명하지 못한다. ADMIN 은 지갑도 가질 수 없어({@code canOwnWallet()} = false) 서명이
     * 필요한 흐름에 들어갈 수 없고, 열어 두면 <b>서버 DB 만 바뀌고 체인은 그대로</b>인
     * 상태를 만들 수 있다.
     *
     * <p><b>{@code /admin/*} 는 다루지 않는다 — §6-6 보류.</b> 관리 엔드포인트가 하나도 없고
     * 권한 범위도 결정되지 않았다. ADMIN 이 무엇을 <b>할 수 있어야</b> 하는지는 이 테스트가
     * 정하지 않는다. 지금 표에 있는 칸에서 거부되는 것만 확인한다.
     */
    @Test
    @DisplayName("§4 ADMIN 은 어떤 쓰기 칸도 통과하지 못한다")
    void adminHasNoAllowedWriteCell() throws Exception {
        List<String[]> writes = List.of(
                new String[] {"POST", "/api/v1/wallets/nonce"},
                new String[] {"POST", "/api/v1/wallets"},
                new String[] {"POST", "/api/materials"},
                new String[] {"POST", "/api/v1/listings"},
                new String[] {"POST", "/api/v1/trades"},
                new String[] {"POST", "/api/v1/product-models"},
                new String[] {"PATCH", "/api/v1/trades/" + ABSENT_ID + "/ship"},
                new String[] {"POST", "/api/v1/trades/" + ABSENT_ID + "/receive/confirm"},
                new String[] {"POST", "/api/v1/processing-requests"},
                new String[] {"POST", "/api/v1/quotes/" + ABSENT_ID + "/select"},
                new String[] {"POST", "/api/v1/processing-jobs/" + ABSENT_ID + "/payment"});

        for (String[] write : writes) {
            MvcResult result = mockMvc
                    .perform(requestFor(write[0], resolve(write[1]), tokens.get(UserRole.ADMIN)))
                    .andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("%s %s — ADMIN 이 403 이 아니다", write[0], write[1])
                    .isEqualTo(HttpStatus.FORBIDDEN.value());
            assertThat(codeOf(result.getResponse().getContentAsString()))
                    .as("%s %s — ADMIN 거부 코드", write[0], write[1])
                    .isIn(ROLE_DENIAL_CODES);
        }
    }

    // ── 케이스 모델 ──────────────────────────────────────────

    /**
     * 표의 한 칸.
     *
     * <p>{@code expectation} 이 {@code null} 코드를 담으면 "역할 문을 통과했다" 까지만 잰다.
     * 이 record 를 두는 이유는 케이스를 손으로 복사하지 않기 위해서다 — 표가 바뀌면
     * {@link #gate} 호출의 허용 역할 집합만 고치면 다섯 칸이 함께 따라온다.
     */
    private record Case(String method, String path, UserRole role, Expectation expectation) {
        @Override
        public String toString() {
            return "%s %s / %s → %s".formatted(method, path, role, expectation);
        }
    }

    /** 한 칸의 기대. {@code code} 가 {@code null} 이면 "거부되지만 않으면 된다" 다. */
    private record Expectation(HttpStatus status, String code) {

        static Expectation passesRoleGate() {
            return new Expectation(null, null);
        }

        static Expectation denied(HttpStatus status, String code) {
            return new Expectation(status, code);
        }

        boolean isDenial() {
            return code != null;
        }

        @Override
        public String toString() {
            return isDenial() ? status.value() + " " + code : "역할 문 통과";
        }
    }

    /**
     * 한 행(엔드포인트)을 다섯 칸으로 펼친다.
     *
     * <p><b>손으로 다섯 줄을 쓰지 않는 것이 요점이다.</b> 복사하면 표가 바뀔 때 한 줄만
     * 고쳐지고 나머지가 남아, 테스트가 표와 다른 것을 지키게 된다. 여기서는 허용 집합의
     * 여집합이 자동으로 거부가 되므로 어긋날 자리가 없다.
     */
    private static List<Case> gate(String method, String path, Set<UserRole> allowed,
            HttpStatus deniedStatus, String deniedCode) {
        return gate(method, path, allowed, deniedStatus, deniedCode, null, null);
    }

    /** 허용 역할의 기대까지 못박는 형태. {@code @PreAuthorize} 칸에서 쓴다. */
    private static List<Case> gate(String method, String path, Set<UserRole> allowed,
            HttpStatus deniedStatus, String deniedCode,
            HttpStatus allowedStatus, String allowedCode) {
        List<Case> cases = new ArrayList<>();
        for (UserRole role : UserRole.values()) {
            Expectation expectation = allowed.contains(role)
                    ? (allowedCode == null
                            ? Expectation.passesRoleGate()
                            : Expectation.denied(allowedStatus, allowedCode))
                    : Expectation.denied(deniedStatus, deniedCode);
            cases.add(new Case(method, path, role, expectation));
        }
        return cases;
    }

    private void assertCase(Case testCase) throws Exception {
        ResultActions actions =
                mockMvc.perform(requestFor(testCase.method(), resolve(testCase.path()),
                        tokens.get(testCase.role())));

        if (testCase.expectation().isDenial()) {
            actions.andExpect(status().is(testCase.expectation().status().value()))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(testCase.expectation().code()))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.data").value(nullValue()))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
            return;
        }

        MvcResult result = actions.andReturn();
        assertThat(result.getResponse().getStatus())
                .as("%s — 인증이 통과하지 못했다", testCase)
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(codeOf(result.getResponse().getContentAsString()))
                .as("%s — 허용 칸인데 역할 문에서 막혔다", testCase)
                .isNotIn(ROLE_DENIAL_CODES);
    }

    // ── 요청 조립 ────────────────────────────────────────────

    /** 표의 경로 템플릿에 시드 id 를 꽂는다. 케이스 목록을 static 으로 유지하기 위한 장치다. */
    private String resolve(String path) {
        return path.replace("{material}", String.valueOf(materialId))
                .replace("{listing}", String.valueOf(listingId))
                .replace("{trade}", String.valueOf(tradeId));
    }

    private MockHttpServletRequestBuilder requestFor(String method, String path, String token)
            throws Exception {
        MockHttpServletRequestBuilder builder = switch (method) {
            case "GET" -> MockMvcRequestBuilders.get(path);
            case "POST" -> MockMvcRequestBuilders.post(path);
            case "PUT" -> MockMvcRequestBuilders.put(path);
            case "PATCH" -> MockMvcRequestBuilders.patch(path);
            case "DELETE" -> MockMvcRequestBuilders.delete(path);
            default -> throw new IllegalArgumentException("지원하지 않는 메서드: " + method);
        };
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        String body = bodyFor(method, path);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return builder;
    }

    /**
     * 그 경로가 요구하는 <b>형식이 유효한</b> 바디.
     *
     * <p>바디가 유효해야 하는 이유는 {@code @Valid} 가 컨트롤러 진입 시점에 돌아 400 을
     * 내기 때문이다 — 형식이 깨진 바디를 보내면 <b>역할 문에 닿기 전에</b> 끝나고,
     * 그 400 이 "거부됐다" 로 오해된다. 값은 존재하지 않는 id 를 쓴다: 역할 검사가 조회보다
     * 먼저 오므로 판정에 영향이 없고, 통과한 요청이 실제로 무언가를 바꾸지도 않는다.
     */
    private String bodyFor(String method, String path) throws Exception {
        String bare = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        if (bare.equals("/api/materials")) {
            return json(Map.of("materialType", "DENIM", "rootOrigin", "WASTE",
                    "name", "매트릭스 소재", "grade", "A", "quantity", 1, "unit", "KG"));
        }
        if (bare.equals("/api/v1/listings")) {
            return json(Map.of("materialId", ABSENT_ID, "quantity", 1, "unit", "KG",
                    "pricePerKg", 4_000));
        }
        if (bare.equals("/api/v1/trades")) {
            return json(tradeBody(ABSENT_ID));
        }
        if (bare.equals("/api/v1/product-models")) {
            return json(Map.of("name", "매트릭스 제품", "category", "BAG", "price", 129_000,
                    "description", "설명", "thumbnailUrl", "/api/v1/product-models/1/thumbnail"));
        }
        if (bare.equals("/api/v1/wallets/nonce")) {
            return json(Map.of("walletAddress", "0xffff000000000000000000000000000000000009",
                    "chainId", CHAIN_ID));
        }
        if (bare.equals("/api/v1/wallets") && method.equals("POST")) {
            return json(Map.of("walletAddress", "0xffff000000000000000000000000000000000009",
                    "chainId", CHAIN_ID, "nonce", "0".repeat(32), "signature", "0x" + "1".repeat(130)));
        }
        if (bare.startsWith("/api/v1/product-models") && bare.endsWith("/status")) {
            return json(Map.of("action", "DISCONTINUE"));
        }
        if (bare.endsWith("/ship")) {
            return json(Map.of("carrier", "CJ대한통운", "trackingNumber", "1234567890"));
        }
        if (bare.endsWith("/payment/approve")) {
            return json(Map.of("pgToken", "matrix-pg-token"));
        }
        if (bare.endsWith("/processing-requests")) {
            return json(Map.of("materialId", ABSENT_ID, "processorId", ABSENT_ID,
                    "quantity", 1_000, "processTypes", List.of("CLEANING")));
        }
        if (bare.endsWith("/quotes") && method.equals("POST")) {
            return json(Map.of("price", 1_000, "leadDays", 3));
        }
        if (bare.endsWith("/select")) {
            return json(Map.of("paymentMethod", "KAKAO_PAY"));
        }
        if (bare.endsWith("/payment/confirm")) {
            return json(Map.of("pgToken", "matrix-pg-token"));
        }
        return null;
    }

    // ── 시드 ─────────────────────────────────────────────────

    /** 사업자 가입 → 이메일 로그인 → 지갑 연결. {@code RoleJourneyTests} 와 같은 순서다. */
    private String signUpBusinessAndLogin(UserRole role, String walletAddress) throws Exception {
        String email = "matrix-" + role.name().toLowerCase(Locale.ROOT) + "@test";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/signup/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(businessSignupBody(email, role))))
                .andExpect(status().isOk());
        String token = login(email);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        walletRepository.save(UserWallet.link(userId, walletAddress, CHAIN_ID));
        return token;
    }

    /** 소비자 가입 → 로그인. <b>지갑을 붙이지 않는다</b> — {@code canOwnWallet()} 이 false 다. */
    private String signUpConsumerAndLogin() throws Exception {
        String email = "matrix-consumer@test";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/signup/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "소비자", "email", email,
                                "phone", "010-0000-0009", "password", PASSWORD,
                                "zipCode", "06236",
                                "address", "서울특별시 강남구 테헤란로 212",
                                "addressDetail", "5층"))))
                .andExpect(status().isOk());
        return login(email);
    }

    /**
     * ADMIN 은 <b>리포지토리로 직접 만든다.</b>
     *
     * <p>가입 API 로 만들 수 없다 — {@code SignupService} 가 {@code canOwnWallet()} 로
     * 막는다({@link #businessSignupRefusesConsumerAndAdmin} 가 그 사실을 고정한다).
     * 즉 ADMIN 계정은 DB 에 직접 넣어야만 존재하고, 그것이 §4 가 ADMIN 을 만능으로 두지 않은
     * 근거 중 하나다. 토큰은 이메일 로그인으로 받는다 — {@code EmailLoginService} 는 역할을
     * 보지 않으므로 ADMIN 도 로그인할 수 있고, 그것이 §4 가 열거한 세 가지 중 첫째다.
     */
    private String createAdminDirectlyAndLogin() throws Exception {
        String email = "matrix-admin@test";
        userRepository.save(User.register(
                email, "{noop}" + PASSWORD, "관리자", "010-0000-0010", UserRole.ADMIN));
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("accessToken").asText();
    }

    private long registerMaterial() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("materialType", "DENIM", "rootOrigin", "WASTE",
                                "name", "매트릭스 데님", "grade", "A", "quantity", 1_000, "unit", "KG"))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("materialId").asLong();
    }

    private long openListing() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("materialId", materialId, "quantity", 100,
                                "unit", "KG", "pricePerKg", 4_000))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("listingId").asLong();
    }

    /**
     * BRAND 가 일부를 산다. 당사자 검사 칸을 두드릴 거래가 하나 필요하다.
     *
     * <p>결제까지 진행하지 않는다 — {@code CREATED} 상태여야 취소·결제 준비 칸이 <b>당사자
     * 검사에서</b> 갈린다. 결제를 마치면 상태 검사가 먼저 걸려 무엇이 거부했는지 흐려진다.
     */
    private long buyPartOfTheListing() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(UserRole.BRAND))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(tradeBody(listingId))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("tradeId").asLong();
    }

    private Map<String, Object> tradeBody(long listing) {
        return Map.of("listingId", listing, "quantity", 10, "unit", "KG",
                "paymentMethod", "KAKAO_PAY", "receiverName", "받는이",
                "receiverPhone", "010-1111-2222", "zipCode", "12345", "address", "부산시 강서구");
    }

    private Map<String, Object> businessSignupBody(String email, UserRole role) {
        return Map.ofEntries(
                Map.entry("role", role.name()),
                Map.entry("companyName", "매트릭스컴퍼니"),
                // 사업자번호는 계정마다 달라야 하고 `000-00-00000` 형식을 지켜야 한다.
                Map.entry("businessNumber",
                        "321-45-%05d".formatted(Math.abs(email.hashCode()) % 100_000)),
                Map.entry("representativeName", "대표자"),
                Map.entry("companyPhone", "051-000-0000"),
                Map.entry("zipCode", "12345"),
                Map.entry("address", "부산시 강서구"),
                Map.entry("addressDetail", "1층"),
                Map.entry("email", email),
                Map.entry("password", PASSWORD));
    }

    // ── 거들기 ───────────────────────────────────────────────

    /** 봉투가 아닌 응답(빈 바디·파일 바이트)도 오므로 {@code code} 를 관대하게 읽는다. */
    private String codeOf(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.path("code").asText("");
        } catch (Exception notAnEnvelope) {
            return "";
        }
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static org.junit.jupiter.params.provider.Arguments args(Object... values) {
        return org.junit.jupiter.params.provider.Arguments.of(values);
    }

    /** FK 역순으로 지운다. */
    private void clean() {
        evidenceFileRepository.deleteAll();
        companyProfileRepository.deleteAll();
        shippingRepository.deleteAll();
        tradeRepository.deleteAll();
        listingRepository.deleteAll();
        lockRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        walletNonceRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
