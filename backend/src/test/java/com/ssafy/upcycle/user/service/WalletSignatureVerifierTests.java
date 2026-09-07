package com.ssafy.upcycle.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.ssafy.upcycle.user.WalletTestSignatures.KEY_PAIR;
import static com.ssafy.upcycle.user.WalletTestSignatures.sign;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.WalletAuthErrorCode;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.utils.Numeric;

/**
 * 서명 복구를 실제 키로 검증한다. Spring 컨텍스트도 지갑 확장도 필요 없다.
 *
 * <p>개인키를 고정해 둔 이유는 재현성이다. {@code Keys.createEcKeyPair()} 로 매번 새로 만들면
 * 실패했을 때 어떤 입력이었는지 알 수 없다. 이 키는 테스트 전용이며 어떤 자산도 담고 있지 않다.
 */
class WalletSignatureVerifierTests {

    private static final Credentials CREDENTIALS = Credentials.create(KEY_PAIR);

    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    private final WalletSignatureVerifier verifier = new WalletSignatureVerifier();

    @Test
    void recoversTheSignerAddress() {
        String message = "hello";

        String recovered = verifier.recoverAddress(message, sign(message, KEY_PAIR));

        assertThat(recovered).isEqualTo(CREDENTIALS.getAddress().toLowerCase(Locale.ROOT));
    }

    @Test
    void recoversFromTheKoreanLoginMessage() {
        // 이 테스트가 이 클래스의 존재 이유다. EIP-191 프리픽스의 길이를 문자 수로 계산하면
        // 한글 메시지에서만 조용히 깨진다 — 위의 'hello' 케이스는 그대로 통과한다.
        String message = WalletLoginMessage.build(CREDENTIALS.getAddress(), NONCE);

        String recovered = verifier.recoverAddress(message, sign(message, KEY_PAIR));

        assertThat(recovered).isEqualTo(CREDENTIALS.getAddress().toLowerCase(Locale.ROOT));
    }

    @Test
    void alwaysReturnsLowerCaseWithPrefix() {
        String message = WalletLoginMessage.build(CREDENTIALS.getAddress(), NONCE);

        String recovered = verifier.recoverAddress(message, sign(message, KEY_PAIR));

        assertThat(recovered).startsWith("0x").hasSize(42).isLowerCase();
    }

    @Test
    void acceptsRecoveryIdZeroOrOneInsteadOfTwentySevenOrEight() {
        // 일부 지갑은 v 를 0/1 로 보낸다. 서명 자체는 같으므로 같은 주소가 나와야 한다.
        String message = WalletLoginMessage.build(CREDENTIALS.getAddress(), NONCE);
        byte[] raw = Numeric.hexStringToByteArray(sign(message, KEY_PAIR));
        raw[64] = (byte) (raw[64] - 27);

        String recovered = verifier.recoverAddress(message, Numeric.toHexString(raw));

        assertThat(recovered).isEqualTo(CREDENTIALS.getAddress().toLowerCase(Locale.ROOT));
    }

    @Test
    void rejectsOutOfRangeRecoveryId() {
        String message = "hello";
        byte[] raw = Numeric.hexStringToByteArray(sign(message, KEY_PAIR));
        raw[64] = 29;

        assertThatThrownBy(() -> verifier.recoverAddress(message, Numeric.toHexString(raw)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(WalletAuthErrorCode.INVALID_SIGNATURE);
    }

    @Test
    void rejectsSignatureOfWrongLength() {
        assertThatThrownBy(() -> verifier.recoverAddress("hello", "0x" + "ab".repeat(64)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> verifier.recoverAddress("hello", "0x" + "ab".repeat(66)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNonHexSignature() {
        assertThatThrownBy(() -> verifier.recoverAddress("hello", "not-a-signature"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsBlankSignature() {
        assertThatThrownBy(() -> verifier.recoverAddress("hello", "  "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aSignatureOverAnotherMessageRecoversADifferentAddress() {
        // 서비스 쪽 비교(recovered vs 요청 주소)가 이 성질에 기대고 있다.
        String signed = WalletLoginMessage.build(CREDENTIALS.getAddress(), NONCE);
        String other = WalletLoginMessage.build(CREDENTIALS.getAddress(), "ffffffffffffffffffffffffffffffff");

        String recovered = verifier.recoverAddress(other, sign(signed, KEY_PAIR));

        assertThat(recovered).isNotEqualTo(CREDENTIALS.getAddress().toLowerCase(Locale.ROOT));
    }

}
