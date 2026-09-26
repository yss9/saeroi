package com.ssafy.upcycle.production.service;

import com.ssafy.upcycle.production.entity.ProductionBatch;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * {@code batch_code} 발급. {@code MaterialCodeGenerator} 와 같은 구조다.
 *
 * <p><b>번호를 세지 않는다.</b> {@code MAX(id) + 1} 이나 {@code count() + 1} 은 두 요청이
 * 같은 값을 읽는 순간 UNIQUE 위반으로 터진다. 실제 순번은 DB 가 AUTO_INCREMENT 로 이미
 * 매기고 있으므로 그 {@code id} 를 형식만 바꿔 쓴다.
 *
 * <pre>
 *   1. placeholder()   "TMP-" + 16 hex     (정확히 20자)
 *   2. save()          id 확정
 *   3. of(id)          "PB-%06d" 로 교체   (같은 트랜잭션 안)
 * </pre>
 */
@Component
public class ProductionBatchCodeGenerator {

    private static final int CODE_MAX_LENGTH = ProductionBatch.BATCH_CODE_MAX_LENGTH;
    private static final String PLACEHOLDER_PREFIX = ProductionBatch.TEMPORARY_CODE_PREFIX;
    private static final String CODE_PREFIX = "PB-";
    private static final int SEQUENCE_DIGITS = 6;
    private static final int PLACEHOLDER_RANDOM_BYTES = 8; // hex 16자 → "TMP-" 포함 20자

    private final SecureRandom random = new SecureRandom();

    /**
     * INSERT 를 통과시키기 위한 임시 코드.
     *
     * <p>{@link SecureRandom} 을 쓰는 것은 비밀이 필요해서가 아니라, 같은 밀리초에 들어온
     * 두 요청이 같은 값을 뽑지 않게 하려는 것이다.
     */
    public String placeholder() {
        byte[] bytes = new byte[PLACEHOLDER_RANDOM_BYTES];
        random.nextBytes(bytes);
        String candidate = PLACEHOLDER_PREFIX + HexFormat.of().formatHex(bytes);
        if (candidate.length() != CODE_MAX_LENGTH) {
            throw new IllegalStateException(
                    "Production batch placeholder code must be exactly "
                            + CODE_MAX_LENGTH + " characters but was " + candidate.length());
        }
        return candidate;
    }

    /** 확정된 {@code id} 로 정식 코드를 만든다. */
    public String of(long productionBatchId) {
        if (productionBatchId <= 0) {
            throw new IllegalArgumentException(
                    "Production batch id must be positive but was " + productionBatchId);
        }
        String code = CODE_PREFIX + String.format("%0" + SEQUENCE_DIGITS + "d", productionBatchId);
        if (code.length() > CODE_MAX_LENGTH) {
            throw new IllegalStateException(
                    "Production batch code must not exceed " + CODE_MAX_LENGTH + " characters: " + code);
        }
        return code;
    }
}
