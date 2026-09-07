package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.material.entity.Material;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * {@code material_code} 발급.
 *
 * <p><b>번호를 세지 않는다.</b> {@code MAX(id) + 1} 이나 {@code count() + 1} 은 두 요청이
 * 같은 값을 읽는 순간 {@code uk_material_code} 위반으로 터진다. 실제 순번은 DB 가
 * AUTO_INCREMENT 로 이미 매기고 있으므로, 그 {@code id} 를 형식만 바꿔 쓴다.
 *
 * <p>그런데 {@code material_code} 는 {@code VARCHAR(20) NOT NULL} UNIQUE 라
 * INSERT 시점에 값이 있어야 한다. 그래서 2단계다.
 *
 * <pre>
 *   1. placeholder()      "TMP-" + 16 hex        (정확히 20자, 충돌 사실상 불가)
 *   2. save()             id 확정
 *   3. of(id)             "MAT-%06d" 로 교체     (같은 트랜잭션 안)
 * </pre>
 *
 * <p>임시값이 밖으로 새면 유일하지만 의미 없는 코드가 남는다. 호출부가
 * {@code @Transactional} 안에서 3단계를 끝내는 것이 계약이고, 테스트가
 * {@code TMP-} 로 시작하는 행이 남지 않음을 고정한다.
 */
@Component
public class MaterialCodeGenerator {

    /**
     * {@code material_code} 컬럼 길이와 임시 코드 접두어는 <b>엔티티가 소유한 불변식</b>이다.
     * {@code Material.assignCode} 가 접두어로 교체 가능 여부를 판정하므로 여기서 다시 정의하지
     * 않고 참조한다 — 두 곳에 두면 한쪽만 바뀌는 순간 교체가 조용히 실패한다.
     */
    private static final int CODE_MAX_LENGTH = Material.MATERIAL_CODE_MAX_LENGTH;

    private static final String PLACEHOLDER_PREFIX = Material.TEMPORARY_CODE_PREFIX;
    private static final String CODE_PREFIX = "MAT-";
    private static final int SEQUENCE_DIGITS = 6;
    private static final int PLACEHOLDER_RANDOM_BYTES = 8; // hex 16자 → "TMP-" 포함 20자

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
        String candidate = PLACEHOLDER_PREFIX + HexFormat.of().formatHex(bytes);
        if (candidate.length() != CODE_MAX_LENGTH) {
            throw new IllegalStateException(
                    "Material placeholder code must be exactly "
                            + CODE_MAX_LENGTH
                            + " characters but was "
                            + candidate.length());
        }
        return candidate;
    }

    /**
     * 확정된 {@code id} 로 정식 코드를 만든다.
     *
     * <p>6자리를 넘는 id 는 0 패딩 없이 그대로 길어진다 — 100만 건을 넘겨도 코드가 깨지지 않는
     * 편이 낫다. 다만 {@code VARCHAR(20)} 은 넘을 수 없으므로 여기서 막는다
     * ({@code long} 최대값도 19자리라 실제로는 도달하지 않는다).
     */
    public String of(long materialId) {
        if (materialId <= 0) {
            throw new IllegalArgumentException("Material id must be positive but was " + materialId);
        }
        String code = CODE_PREFIX + String.format("%0" + SEQUENCE_DIGITS + "d", materialId);
        if (code.length() > CODE_MAX_LENGTH) {
            throw new IllegalStateException(
                    "Material code must not exceed " + CODE_MAX_LENGTH + " characters: " + code);
        }
        return code;
    }
}
