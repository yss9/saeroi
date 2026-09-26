package com.ssafy.upcycle.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 개인회원 가입이 <b>기본 배송지까지 만든다</b>.
 *
 * <p>계정만 만들어 두고 주소를 흘리면 사용자는 가입 화면에 분명히 입력했는데 주문할 때
 * 다시 묻는 상태가 된다 — 프론트가 보낸 필드를 서버가 조용히 버리던 것이 이 기능 이전의
 * 상태였고, 그게 이 테스트가 막는 회귀다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsumerSignupAddressTests {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsumerAddressRepository addressRepository;

    @BeforeEach
    void setUp() {
        clean();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void signingUpCreatesExactlyOneDefaultAddress() throws Exception {
        signUp(body("addr-signup@example.com")).andExpect(status().isOk());

        Long userId = userRepository.findByEmail("addr-signup@example.com").orElseThrow().getId();
        assertThat(addressRepository.findAllByUserIdOrderByIsDefaultDescIdAsc(userId))
                .singleElement()
                .satisfies(address -> {
                    assertThat(address.isDefault()).isTrue();
                    assertThat(address.getZipCode()).isEqualTo("06236");
                    assertThat(address.getAddress()).isEqualTo("서울특별시 강남구 테헤란로 212");
                    assertThat(address.getAddressDetail()).isEqualTo("5층");
                });
    }

    @Test
    void theFirstReceiverIsTheAccountHolder() throws Exception {
        // 가입 폼에는 수령인 칸이 없다. 비워 두면 송장에 받는 사람이 없는 배송지가 남는다.
        signUp(body("addr-receiver@example.com")).andExpect(status().isOk());

        Long userId = userRepository.findByEmail("addr-receiver@example.com").orElseThrow().getId();
        ConsumerAddress address = addressRepository.findByUserIdAndIsDefaultTrue(userId).orElseThrow();
        assertThat(address.getReceiverName()).isEqualTo("홍길동");
        assertThat(address.getReceiverPhone()).isEqualTo("010-1234-5678");
        assertThat(address.getAlias()).isEqualTo(ConsumerAddress.DEFAULT_ALIAS);
    }

    @Test
    void anEmptyAddressDetailIsStoredAsNothingAtAll() throws Exception {
        // 프론트가 입력 없이 빈 문자열을 보낸다고 알려 왔다.
        Map<String, Object> request = body("addr-blank-detail@example.com");
        request.put("addressDetail", "");

        signUp(request).andExpect(status().isOk());

        Long userId = userRepository.findByEmail("addr-blank-detail@example.com").orElseThrow().getId();
        assertThat(addressRepository.findByUserIdAndIsDefaultTrue(userId).orElseThrow()
                .getAddressDetail()).isNull();
    }

    @Test
    void signingUpWithoutAnAddressIsRefused() throws Exception {
        // 주소 없이 가입이 되면 "가입은 됐는데 주소만 없는" 계정이 다시 늘어난다.
        Map<String, Object> request = body("addr-missing@example.com");
        request.remove("address");

        signUp(request).andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmail("addr-missing@example.com")).isEmpty();
    }

    @Test
    void aRefusedSignupLeavesNeitherAccountNorAddress() throws Exception {
        // 계정만 남고 주소가 없으면 로그인은 되는데 주문이 안 되는 계정이 생긴다.
        signUp(body("addr-rollback@example.com")).andExpect(status().isOk());

        signUp(body("addr-rollback@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));

        Long userId = userRepository.findByEmail("addr-rollback@example.com").orElseThrow().getId();
        assertThat(addressRepository.countByUserId(userId)).isEqualTo(1);
    }

    // ── helpers ─────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions signUp(Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup/consumer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> body(String email) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "홍길동");
        request.put("email", email);
        request.put("phone", "010-1234-5678");
        request.put("password", PASSWORD);
        request.put("zipCode", "06236");
        request.put("address", "서울특별시 강남구 테헤란로 212");
        request.put("addressDetail", "5층");
        return request;
    }

    private void clean() {
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }
}
