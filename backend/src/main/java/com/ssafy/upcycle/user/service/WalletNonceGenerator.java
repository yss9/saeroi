package com.ssafy.upcycle.user.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 지갑 서명용 1회용 nonce 를 만든다. 로그인과 등록이 같은 생성기를 쓴다.
 *
 * <p>한 곳에 둔 이유는 길이 계약이다. {@code wallet_nonce.nonce} 가 {@code CHAR(32)} 이고
 * 요청 DTO 의 검증 정규식도 {@code ^[0-9a-f]{32}$} 라, 생성 방식이 두 곳에 흩어지면
 * 한쪽만 바뀌어 조용히 어긋난다.
 */
@Component
public class WalletNonceGenerator {

    /** 16 bytes → hex 32자. CHAR(32) 를 정확히 채운다. */
    private static final int NONCE_BYTES = 16;

    /**
     * {@code SecureRandom.getInstanceStrong()} 을 쓰지 않는다. 리눅스에서 엔트로피를 기다리며
     * 블로킹할 수 있는데, 로그인 경로에서 그런 정지는 곤란하다. 기본 SecureRandom 의
     * 128비트로 충분하다 — nonce 는 수 분만 살아 있고 지갑 주소로 범위가 한정된다.
     */
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        // hex 는 고정 길이다. Base64 는 '=' 패딩과 가변 길이 때문에 컬럼 길이도 안 맞고
        // 서명 문자열에 특수문자가 섞인다.
        return HexFormat.of().formatHex(bytes);
    }
}
