package com.ssafy.upcycle.user;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * 테스트에서 진짜 지갑 서명을 만든다.
 *
 * <p>여러 테스트 패키지(service, controller)가 함께 쓰므로 public 이다 — 이 저장소의 테스트
 * 클래스는 보통 package-private 이지만, 이건 테스트 클래스가 아니라 지원 유틸이다.
 *
 * <p>키를 고정해 두는 이유는 재현성이다. 매번 새로 만들면 실패했을 때 어떤 입력이었는지 알 수 없다.
 * 아래 두 키는 Hardhat 기본 계정 #0, #1 의 것으로 공개된 값이며 테스트 체인 밖에서는 의미가 없다.
 */
public final class WalletTestSignatures {

    public static final ECKeyPair KEY_PAIR = ECKeyPair.create(new BigInteger(
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80", 16));

    /** 남의 서명을 들고 오는 상황을 만들 때 쓴다. */
    public static final ECKeyPair OTHER_KEY_PAIR = ECKeyPair.create(new BigInteger(
            "59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d", 16));

    private WalletTestSignatures() {
    }

    /** 지갑의 personal_sign 과 같은 형식(0x + r|s|v, 130자). */
    public static String sign(String message, ECKeyPair keyPair) {
        Sign.SignatureData data =
                Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), keyPair);
        return Numeric.toHexString(data.getR())
                + Numeric.toHexStringNoPrefix(data.getS())
                + Numeric.toHexStringNoPrefix(data.getV());
    }
}
