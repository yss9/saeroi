package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.product.entity.ProductOrder;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * {@code order_code} 발급.
 *
 * <p><b>번호를 세지 않는다.</b> {@code count() + 1} 은 두 요청이 같은 값을 읽는 순간
 * {@code uk_product_order_code} 위반으로 터진다. 실제 순번은 DB 가 AUTO_INCREMENT 로 이미
 * 매기고 있으므로 그 {@code id} 를 형식만 바꿔 쓴다 — {@code MaterialCodeGenerator} 와 같은
 * 판단이다.
 *
 * <p>{@code order_code} 는 NOT NULL UNIQUE 라 INSERT 시점에 값이 있어야 한다. 그래서 2단계다.
 *
 * <pre>
 *   1. placeholder()      "TMP-" + 16 hex        (충돌 사실상 불가)
 *   2. saveAndFlush()     id 확정
 *   3. of(id)             "ORD-%08d" 로 교체     (같은 트랜잭션 안)
 * </pre>
 *
 * <p>임시값이 밖으로 새면 고객이 문의할 수 없는 주문번호가 남는다. 호출부가
 * {@code @Transactional} 안에서 3단계를 끝내는 것이 계약이다.
 *
 * <p>랜덤 값을 사람이 읽는 최종 코드로 쓰지 않는 이유는, 주문번호가 전화나 채팅으로
 * 불러 주는 값이기 때문이다. {@code ORD-00000031} 은 받아 적을 수 있지만 16진수 32자는
 * 그렇지 않다.
 */
@Component
public class ProductOrderCodeGenerator {

    private static final int CODE_MAX_LENGTH = ProductOrder.ORDER_CODE_MAX_LENGTH;
    private static final String PLACEHOLDER_PREFIX = ProductOrder.TEMPORARY_CODE_PREFIX;
    private static final String CODE_PREFIX = "ORD-";
    private static final int SEQUENCE_DIGITS = 8;
    private static final int PLACEHOLDER_RANDOM_BYTES = 8;

    private final SecureRandom random = new SecureRandom();

    /**
     * INSERT 를 통과시키기 위한 임시 코드.
     *
     * <p>{@link SecureRandom} 을 쓰는 것은 비밀이 필요해서가 아니라, 같은 밀리초에 들어온
     * 두 요청이 같은 값을 뽑지 않게 하려는 것이다. 시간 기반 값은 그 보장이 없다.
     */
    public String placeholder() {
        byte[] bytes = new byte[PLACEHOLDER_RANDOM_BYTES];
        random.nextBytes(bytes);
        return PLACEHOLDER_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * 확정된 {@code id} 로 정식 주문번호를 만든다.
     *
     * <p>8자리를 넘는 id 는 0 패딩 없이 그대로 길어진다 — 1억 건을 넘겨도 코드가 깨지지
     * 않는 편이 낫다. {@code VARCHAR(30)} 은 넘을 수 없으므로 여기서 막는다.
     */
    public String of(long orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Product order id must be positive but was " + orderId);
        }
        String code = CODE_PREFIX + String.format("%0" + SEQUENCE_DIGITS + "d", orderId);
        if (code.length() > CODE_MAX_LENGTH) {
            throw new IllegalStateException(
                    "Product order code must not exceed " + CODE_MAX_LENGTH + " characters: " + code);
        }
        return code;
    }
}
