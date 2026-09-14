package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code /api/v1/consumers/me/addresses} — 소비자 배송지 주소록.
 *
 * <p>여기서 지키는 것은 두 가지다. <b>기본 배송지는 사용자당 하나</b>이고,
 * <b>남의 주소록은 보이지도 만져지지도 않는다</b>. 전자는 MySQL 8 에 부분 UNIQUE 인덱스가
 * 없어 DB 가 막아 주지 못하고, 후자는 틀렸을 때 남의 집 주소가 새어 나간다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsumerAddressControllerTests {

    private static final String BASE = "/api/v1/consumers/me/addresses";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsumerAddressRepository addressRepository;

    private Long consumerId;
    private Long otherConsumerId;
    private Long brandId;

    @BeforeEach
    void setUp() {
        clean();
        consumerId = saveUser("addr-consumer@test.com", "홍길동", "010-1111-2222", UserRole.CONSUMER);
        otherConsumerId = saveUser("addr-other@test.com", "김이웃", "010-3333-4444", UserRole.CONSUMER);
        brandId = saveUser("addr-brand@test.com", "김대표", "02-1234-5678", UserRole.BRAND);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 조회 ────────────────────────────────────────────────

    @Test
    void anAccountWithNoAddressGetsAnEmptyListNotAnError() throws Exception {
        // 이 기능 이전에 가입한 소비자가 이 상태다. 404 로 답하면 화면이 오류로 처리한다.
        mockMvc.perform(withJwt(get(BASE), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void theDefaultAddressComesFirstRegardlessOfWhenItWasAdded() throws Exception {
        // 화면은 대개 첫 줄을 바로 고른다. 정렬을 클라이언트에 맡기면 구현마다 달라진다.
        Long first = saved(consumerId, "집", true);
        Long second = saved(consumerId, "회사", false);
        Long third = saved(consumerId, "부모님 댁", false);
        setDefault(third);

        mockMvc.perform(withJwt(get(BASE), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].addressId").value(third))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                // 나머지는 등록 순서다 — 사용자가 기억하는 순서가 곧 그 순서다.
                .andExpect(jsonPath("$.data[1].addressId").value(first))
                .andExpect(jsonPath("$.data[2].addressId").value(second));
    }

    @Test
    void oneConsumerNeverSeesAnothersAddresses() throws Exception {
        saved(otherConsumerId, "남의 집", true);

        mockMvc.perform(withJwt(get(BASE), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ── 등록 ────────────────────────────────────────────────

    @Test
    void theFirstAddressBecomesTheDefaultWithoutBeingAsked() throws Exception {
        // 고를 수 있는 것이 그것뿐이라 물을 필요가 없다. 기본이 비어 있으면 첫 주문 화면이
        // 아무것도 선택하지 못한 채 시작한다.
        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("집"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void aSecondAddressDoesNotStealTheDefault() throws Exception {
        saved(consumerId, "집", true);

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("회사"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false));

        assertThat(defaultAliasOf(consumerId)).isEqualTo("집");
    }

    @Test
    void theReceiverCanDifferFromTheAccountHolder() throws Exception {
        // 선물이나 가족 집으로 보내는 경우다. 송장에 찍히는 것은 주문자가 아니라 받는 사람이다.
        Map<String, Object> request = body("부모님 댁");
        request.put("receiverName", "홍부모");
        request.put("receiverPhone", "010-9999-8888");

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiverName").value("홍부모"))
                .andExpect(jsonPath("$.data.receiverPhone").value("010-9999-8888"));
    }

    @Test
    void anEmptyAddressDetailIsStoredAsNothingAtAll() throws Exception {
        // 프론트는 입력 없이 빈 문자열을 보낸다. 그대로 담아 두면 송장에 빈 줄이 인쇄된다.
        Map<String, Object> request = body("집");
        request.put("addressDetail", "   ");

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressDetail").doesNotExist());
    }

    @Test
    void rejectsAnAddressThatCouldNeverBeDelivered() throws Exception {
        Map<String, Object> request = body("집");
        request.put("zipCode", "");

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stopsAddingOnceTheListWouldStopBeingAList() throws Exception {
        // 상한이 없으면 로그인한 계정 하나가 호출 횟수만큼 행을 만들 수 있다.
        for (int i = 0; i < 20; i++) {
            saved(consumerId, "주소" + i, i == 0);
        }

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("스물한 번째"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSUMER_ADDRESS_LIMIT_EXCEEDED"));
    }

    // ── 수정 ────────────────────────────────────────────────

    @Test
    void editingAnAddressLeavesTheDefaultWhereItWas() throws Exception {
        // 주소를 고치다가 기본이 조용히 옮겨 가면, 사용자가 눈치채지 못한 채 다른 곳으로 받는다.
        Long home = saved(consumerId, "집", true);
        Long office = saved(consumerId, "회사", false);

        Map<String, Object> request = body("회사(이전)");
        mockMvc.perform(withJwt(put(BASE + "/" + office), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value("회사(이전)"))
                .andExpect(jsonPath("$.data.isDefault").value(false));

        assertThat(addressRepository.findById(home).orElseThrow().isDefault()).isTrue();
    }

    @Test
    void editingSomeoneElsesAddressLooksExactlyLikeEditingOneThatIsNotThere() throws Exception {
        // 403 으로 답하면 "그 id 는 존재하고 남의 것" 이 새어 나간다.
        Long theirs = saved(otherConsumerId, "남의 집", true);

        mockMvc.perform(withJwt(put(BASE + "/" + theirs), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("가로채기"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONSUMER_ADDRESS_NOT_FOUND"));

        assertThat(addressRepository.findById(theirs).orElseThrow().getAlias()).isEqualTo("남의 집");
    }

    // ── 기본 지정 ───────────────────────────────────────────

    @Test
    void settingANewDefaultTakesItFromTheOldOne() throws Exception {
        Long home = saved(consumerId, "집", true);
        Long office = saved(consumerId, "회사", false);

        mockMvc.perform(withJwt(patch(BASE + "/" + office + "/default"), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        assertThat(addressRepository.findById(home).orElseThrow().isDefault()).isFalse();
        assertThat(defaultCountOf(consumerId)).isEqualTo(1);
    }

    @Test
    void thereIsNeverMoreThanOneDefaultNoMatterHowOftenItMoves() throws Exception {
        // DB 에 이를 막는 제약이 없다(MySQL 8 에는 부분 UNIQUE 인덱스가 없다).
        // 서비스가 유일한 안전장치이므로 반복해서 옮겨 보고 확인한다.
        List<Long> ids = List.of(
                saved(consumerId, "집", true),
                saved(consumerId, "회사", false),
                saved(consumerId, "부모님 댁", false));

        for (Long id : ids) {
            mockMvc.perform(withJwt(patch(BASE + "/" + id + "/default"), consumerId, UserRole.CONSUMER))
                    .andExpect(status().isOk());
            assertThat(defaultCountOf(consumerId)).isEqualTo(1);
        }
        assertThat(addressRepository.findByUserIdAndIsDefaultTrue(consumerId).orElseThrow().getId())
                .isEqualTo(ids.get(2));
    }

    @Test
    void settingTheSameAddressAsDefaultTwiceIsNotAnError() throws Exception {
        Long home = saved(consumerId, "집", true);

        mockMvc.perform(withJwt(patch(BASE + "/" + home + "/default"), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        assertThat(defaultCountOf(consumerId)).isEqualTo(1);
    }

    // ── 삭제 ────────────────────────────────────────────────

    @Test
    void deletingTheDefaultPromotesTheOldestRemainingOne() throws Exception {
        // 기본이 없는 채로 두면 다음 주문 화면이 아무것도 고르지 못한 상태로 시작한다.
        Long home = saved(consumerId, "집", true);
        Long office = saved(consumerId, "회사", false);
        Long parents = saved(consumerId, "부모님 댁", false);

        mockMvc.perform(withJwt(delete(BASE + "/" + home), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk());

        assertThat(addressRepository.findById(office).orElseThrow().isDefault()).isTrue();
        assertThat(addressRepository.findById(parents).orElseThrow().isDefault()).isFalse();
        assertThat(defaultCountOf(consumerId)).isEqualTo(1);
    }

    @Test
    void deletingANonDefaultLeavesTheDefaultAlone() throws Exception {
        Long home = saved(consumerId, "집", true);
        Long office = saved(consumerId, "회사", false);

        mockMvc.perform(withJwt(delete(BASE + "/" + office), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk());

        assertThat(addressRepository.findById(home).orElseThrow().isDefault()).isTrue();
    }

    @Test
    void theLastAddressCanBeDeleted() throws Exception {
        // "최소 한 건" 으로 막지 않는다 — 이 기능 이전 가입자는 이미 0건이라, 그들에게는
        // 허용되고 새 가입자에게만 금지되는 규칙은 설명할 방법이 없다.
        Long only = saved(consumerId, "집", true);

        mockMvc.perform(withJwt(delete(BASE + "/" + only), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk());

        assertThat(addressRepository.countByUserId(consumerId)).isZero();
    }

    @Test
    void deletingSomeoneElsesAddressDoesNothing() throws Exception {
        Long theirs = saved(otherConsumerId, "남의 집", true);

        mockMvc.perform(withJwt(delete(BASE + "/" + theirs), consumerId, UserRole.CONSUMER))
                .andExpect(status().isNotFound());

        assertThat(addressRepository.findById(theirs)).isPresent();
    }

    // ── 역할 ────────────────────────────────────────────────

    @Test
    void businessAccountsHaveNoAddressBook() throws Exception {
        // 사업자는 company_profile 이 주소를 갖는다. 둘 다 가지면 "어디로 보내느냐" 에
        // 답이 둘이 된다.
        mockMvc.perform(withJwt(get(BASE), brandId, UserRole.BRAND))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSUMER_ADDRESS_FORBIDDEN"));
    }

    @Test
    void theAddressBookNeedsALogin() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder withJwt(
            MockHttpServletRequestBuilder builder, Long userId, UserRole role) {
        return builder.with(jwt()
                .jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private Map<String, Object> body(String alias) {
        Map<String, Object> request = new HashMap<>();
        request.put("alias", alias);
        request.put("receiverName", "홍길동");
        request.put("receiverPhone", "010-1111-2222");
        request.put("zipCode", "06236");
        request.put("address", "서울특별시 강남구 테헤란로 212");
        request.put("addressDetail", "5층");
        return request;
    }

    private Long saved(Long userId, String alias, boolean isDefault) {
        ConsumerAddress address = ConsumerAddress.register(
                userId, alias, "홍길동", "010-1111-2222", "06236", "서울특별시 강남구 테헤란로 212", "5층");
        address.markDefault(isDefault);
        return addressRepository.save(address).getId();
    }

    private void setDefault(Long addressId) throws Exception {
        mockMvc.perform(withJwt(patch(BASE + "/" + addressId + "/default"), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk());
    }

    private long defaultCountOf(Long userId) {
        return addressRepository.findAllByUserIdOrderByIsDefaultDescIdAsc(userId).stream()
                .filter(ConsumerAddress::isDefault)
                .count();
    }

    private String defaultAliasOf(Long userId) {
        return addressRepository.findByUserIdAndIsDefaultTrue(userId).orElseThrow().getAlias();
    }

    private Long saveUser(String email, String name, String phone, UserRole role) {
        return userRepository.save(User.register(email, "{noop}x", name, phone, role)).getId();
    }

    private void clean() {
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }
}
