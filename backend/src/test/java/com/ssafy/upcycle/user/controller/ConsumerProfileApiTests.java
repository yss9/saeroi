package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code /api/v1/consumers/me} — 소비자 본인 개인정보 조회·수정.
 *
 * <p>여기서 지키는 것은 셋이다. <b>본인 정보만 보이고</b>, <b>이름·연락처가 실제로 저장되며</b>,
 * <b>계정 연락처를 고쳐도 배송지의 수령인 연락처는 그대로다</b>. 셋째가 깨지면 가족이나
 * 회사로 받도록 일부러 다르게 둔 값이 예고 없이 덮어써진다.
 *
 * <p>이메일은 수정 대상이 아니다 — 로그인 식별자라 새 주소의 소유 확인이 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsumerProfileApiTests {

    private static final String BASE = "/api/v1/consumers/me";
    private static final String EMAIL = "profile-consumer@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsumerAddressRepository addressRepository;

    private User consumer;
    private User brand;
    private Long addressId;

    @BeforeEach
    void setUp() {
        consumer = userRepository.save(User.register(
                EMAIL, "{noop}x", "홍길동", "010-1111-2222", UserRole.CONSUMER));
        brand = userRepository.save(User.register(
                "profile-brand@example.com", "{noop}x", "김대표", "02-1234-5678", UserRole.BRAND));
        // 가입이 만드는 것과 같은 모양이다 - 계정 정보를 복사해 첫 배송지를 만든다.
        ConsumerAddress address = ConsumerAddress.ofSignup(
                consumer.getId(),
                consumer.getName(),
                consumer.getPhone(),
                "06236",
                "서울특별시 강남구 테헤란로 212",
                "5층");
        address.markDefault(true);
        addressId = addressRepository.save(address).getId();
    }

    // ── 조회 ────────────────────────────────────────────────

    @Test
    void theProfileScreenCanReadTheCurrentValuesFromTheServer() throws Exception {
        // 로그인 응답을 화면이 들고 있다가 쓰면 다른 탭에서 고쳤을 때 어긋난다.
        mockMvc.perform(withJwt(get(BASE), consumer.getId(), UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(consumer.getId()))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.phone").value("010-1111-2222"))
                .andExpect(jsonPath("$.data.role").value("CONSUMER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void readingTheProfileNeedsALogin() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    // ── 수정 ────────────────────────────────────────────────

    @Test
    void theNameAndPhoneAreActuallySaved() throws Exception {
        mockMvc.perform(updateRequest(consumer.getId(), body("김철수", "010-9999-8888")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김철수"))
                .andExpect(jsonPath("$.data.phone").value("010-9999-8888"));

        // 응답만 바뀌고 저장되지 않는 것이 이 작업이 고치려는 목업의 동작이다.
        mockMvc.perform(withJwt(get(BASE), consumer.getId(), UserRole.CONSUMER))
                .andExpect(jsonPath("$.data.name").value("김철수"))
                .andExpect(jsonPath("$.data.phone").value("010-9999-8888"));
        User saved = userRepository.findById(consumer.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("김철수");
        assertThat(saved.getPhone()).isEqualTo("010-9999-8888");
    }

    /**
     * <b>계정 연락처와 수령인 연락처는 다른 값이다.</b>
     *
     * <p>가입 때만 같은 값으로 시작할 뿐, 가족 집이나 회사로 받으면 받는 사람이 본인이
     * 아니다. 여기서 함께 덮어쓰면 사용자가 일부러 다르게 둔 값이 예고 없이 바뀐다.
     */
    @Test
    void changingTheAccountPhoneLeavesTheDeliveryContactAlone() throws Exception {
        mockMvc.perform(updateRequest(consumer.getId(), body("김철수", "010-9999-8888")))
                .andExpect(status().isOk());

        ConsumerAddress address = addressRepository.findById(addressId).orElseThrow();
        assertThat(address.getReceiverName()).isEqualTo("홍길동");
        assertThat(address.getReceiverPhone()).isEqualTo("010-1111-2222");
    }

    @Test
    void theEmailIsNotChangedEvenWhenTheRequestCarriesOne() throws Exception {
        // 로그인 식별자라 새 주소의 소유 확인 없이 바꿀 수 없다. 요청에 섞여 와도 무시한다.
        Map<String, Object> request = body("김철수", "010-9999-8888");
        request.put("email", "hijacked@example.com");

        mockMvc.perform(updateRequest(consumer.getId(), request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));

        assertThat(userRepository.findById(consumer.getId()).orElseThrow().getEmail())
                .isEqualTo(EMAIL);
    }

    @Test
    void aMalformedNameOrPhoneChangesNothing() throws Exception {
        mockMvc.perform(updateRequest(consumer.getId(), body("김", "010-9999-8888")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(updateRequest(consumer.getId(), body("김철수", "02-1234-5678")))
                .andExpect(status().isBadRequest());

        User unchanged = userRepository.findById(consumer.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("홍길동");
        assertThat(unchanged.getPhone()).isEqualTo("010-1111-2222");
    }

    @Test
    void aPhoneWithoutHyphensIsStoredExactlyAsSent() throws Exception {
        // 가입이 입력값을 그대로 저장한다. 여기서만 정규화하면 같은 계정의 값이 가입 전후로
        // 달라진다.
        mockMvc.perform(updateRequest(consumer.getId(), body("홍길동", "01099998888")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("01099998888"));
    }

    // ── 역할 ────────────────────────────────────────────────

    @Test
    void businessAccountsUseTheirOwnPathInstead() throws Exception {
        // 사업자의 같은 칸에는 유선번호가 들어간다. 소비자 형식으로 검증하면 자기 번호를
        // 저장할 수 없게 된다.
        mockMvc.perform(withJwt(get(BASE), brand.getId(), UserRole.BRAND))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSUMER_PROFILE_FORBIDDEN"));

        mockMvc.perform(put(BASE)
                        .with(jwt()
                                .jwt(token -> token.subject(String.valueOf(brand.getId())))
                                .authorities(new SimpleGrantedAuthority("ROLE_BRAND")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("김대표", "010-1111-2222"))))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(brand.getId()).orElseThrow().getName())
                .isEqualTo("김대표");
    }

    @Test
    void aSuspendedAccountCannotEditItsProfile() throws Exception {
        consumer.suspend();
        userRepository.saveAndFlush(consumer);

        mockMvc.perform(updateRequest(consumer.getId(), body("김철수", "010-9999-8888")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSUMER_PROFILE_FORBIDDEN"));
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder updateRequest(Long userId, Map<String, Object> body)
            throws Exception {
        return withJwt(put(BASE), userId, UserRole.CONSUMER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private Map<String, Object> body(String name, String phone) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("phone", phone);
        return request;
    }

    private MockHttpServletRequestBuilder withJwt(
            MockHttpServletRequestBuilder builder, Long userId, UserRole role) {
        return builder.with(jwt()
                .jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
