package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.WalletAuthErrorCode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * 서명에서 서명자 주소를 복원한다 (ecrecover).
 *
 * <p>web3j 를 아는 유일한 클래스로 떼어 두었다. Spring 컨텍스트가 필요 없고 DB 도 만지지 않아
 * 빠른 단위 테스트가 가능하며, 이 기능에서 미묘한 버그가 가장 많이 나는 지점이기 때문이다.
 *
 * <p><b>EIP-191 프리픽스를 직접 만들지 않는다.</b> {@link Sign#signedPrefixedMessageToKey} 가
 * {@code "\x19Ethereum Signed Message:\n" + 길이} 를 붙여 준다. 직접 만들려 들면 길이를
 * {@code String.length()} 로 쓰기 쉬운데, 규격이 요구하는 것은 <b>UTF-8 바이트 길이</b>다.
 * 한글이 섞인 로그인 문구는 문자 수와 바이트 수가 3배 가까이 차이 나므로, 그렇게 만들면
 * 모든 검증이 실패한다. 그래서 항상 {@code getBytes(UTF_8)} 을 넘긴다.
 */
@Component
public class WalletSignatureVerifier {

    /** r(32) + s(32) + v(1). */
    private static final int SIGNATURE_BYTES = 65;

    private static final int R_END = 32;
    private static final int S_END = 64;

    /**
     * @param message 지갑이 서명한 원문 (프리픽스를 붙이기 전 상태)
     * @param signature {@code 0x} 로 시작하는 130자 hex
     * @return 복원된 주소. 언제나 {@code 0x} + 소문자 40자
     * @throws BusinessException 서명이 형식에 맞지 않거나 복원할 수 없을 때
     */
    public String recoverAddress(String message, String signature) {
        byte[] raw = parse(signature);

        byte recoveryId = raw[SIGNATURE_BYTES - 1];
        // MetaMask/ethers 는 27·28 을 쓰지만, 일부 지갑과 라이브러리는 recovery id 를 0·1 로
        // 그대로 내보낸다. web3j 는 27 이상을 기대하므로 여기서 올려 준다.
        if (recoveryId < 27) {
            recoveryId += 27;
        }
        if (recoveryId != 27 && recoveryId != 28) {
            throw new BusinessException(WalletAuthErrorCode.INVALID_SIGNATURE);
        }

        Sign.SignatureData data = new Sign.SignatureData(
                recoveryId, Arrays.copyOfRange(raw, 0, R_END), Arrays.copyOfRange(raw, R_END, S_END));

        try {
            BigInteger publicKey =
                    Sign.signedPrefixedMessageToKey(message.getBytes(StandardCharsets.UTF_8), data);
            // Keys.getAddress 는 0x 없는 소문자 40자를 준다.
            return "0x" + Keys.getAddress(publicKey).toLowerCase(Locale.ROOT);
        } catch (SignatureException | IllegalArgumentException exception) {
            // 원인 메시지와 서명 원문은 로그·응답 어디에도 남기지 않는다.
            throw new BusinessException(WalletAuthErrorCode.INVALID_SIGNATURE);
        }
    }

    private static byte[] parse(String signature) {
        if (signature == null || signature.isBlank()) {
            throw new BusinessException(WalletAuthErrorCode.INVALID_SIGNATURE);
        }
        byte[] raw;
        try {
            // hex 가 아니면 NumberFormatException 이 난다 (IllegalArgumentException 의 하위).
            raw = Numeric.hexStringToByteArray(signature);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(WalletAuthErrorCode.INVALID_SIGNATURE);
        }
        if (raw.length != SIGNATURE_BYTES) {
            throw new BusinessException(WalletAuthErrorCode.INVALID_SIGNATURE);
        }
        return raw;
    }
}
