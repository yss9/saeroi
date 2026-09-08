package com.ssafy.upcycle.product.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 제품 개체의 QR 토큰을 만든다.
 *
 * <p><b>추측 가능하면 안 된다.</b> QR 이력 조회는 인증 없이 열려 있는 경로라, 값을 맞힐 수
 * 있으면 남의 제품 이력을 전부 열람할 수 있다. 개체 id 나 순번을 쓰지 않는 이유가 이것이다.
 *
 * <p>생성을 한 곳에 모은 이유는 길이 계약이다. {@code product_item.qr_token} 이
 * {@code CHAR(32)} 이고 엔티티도 {@code ^[0-9a-f]{32}$} 로 검사하므로, 만드는 방식이 여러
 * 곳에 흩어지면 한쪽만 바뀌어 조용히 어긋난다 — {@code WalletNonceGenerator} 와 같은 이유다.
 */
@Component
public class ProductQrTokenGenerator {

    /** 16 bytes → hex 32자. CHAR(32) 를 정확히 채운다. */
    private static final int TOKEN_BYTES = 16;

    /**
     * {@code SecureRandom.getInstanceStrong()} 을 쓰지 않는다. 리눅스에서 엔트로피를 기다리며
     * 블로킹할 수 있는데, 생산 확정 한 번에 개체가 수십 개씩 만들어지는 경로라 그런 정지는
     * 곤란하다. 기본 SecureRandom 의 128비트로 충분하다.
     */
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
