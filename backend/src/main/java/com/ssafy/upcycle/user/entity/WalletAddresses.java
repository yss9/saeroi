package com.ssafy.upcycle.user.entity;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 지갑 주소 정규화. <b>저장·조회·비교는 언제나 소문자</b>다.
 *
 * <p>대소문자를 섞어 두면 안 되는 이유가 테스트에서 잘 드러나지 않는다.
 * 운영 MySQL 의 {@code utf8mb4_0900_ai_ci} 는 대소문자를 무시하므로
 * {@code uk_user_wallet_address} 가 중복을 막아 주지만, 테스트의 H2 는 대소문자를 구분한다.
 * 정규화 없이 두면 <b>테스트는 초록인데 운영에만 중복 행이 생기는</b> 조합이 된다.
 *
 * <p>화면·서명 메시지에 쓰는 EIP-55 체크섬 형태는 소문자 주소로부터 서버가 직접 계산한다
 * (web3j {@code Keys.toChecksumAddress}). 요청이 보낸 대소문자는 신뢰하지 않는다.
 * 여기에 web3j 를 끌어들이지 않으려고 체크섬 변환은 이 클래스에 두지 않았다.
 */
public final class WalletAddresses {

    /** EIP-55 체크섬 여부와 무관하게 형태만 본다. 검증은 여기, 의미 부여는 호출부. */
    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    public static final int LENGTH = 42;

    private WalletAddresses() {
    }

    /** 형식을 검증하고 소문자로 바꾼다. 형식이 틀리면 {@link IllegalArgumentException}. */
    public static String normalize(String walletAddress) {
        if (walletAddress == null || !ADDRESS.matcher(walletAddress).matches()) {
            throw new IllegalArgumentException("Wallet address must match ^0x[0-9a-fA-F]{40}$");
        }
        return walletAddress.toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String walletAddress) {
        return walletAddress != null && ADDRESS.matcher(walletAddress).matches();
    }
}
