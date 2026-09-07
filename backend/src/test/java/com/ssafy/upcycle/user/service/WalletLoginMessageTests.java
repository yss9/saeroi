package com.ssafy.upcycle.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 브랜치 간 계약을 문자열 리터럴로 고정한다.
 *
 * <p>이 테스트가 깨지면 Frontend 의 {@code buildLoginMessage()} 도 함께 바뀌었는지 확인해야 한다.
 * 한쪽만 바꾸면 컴파일도 되고 테스트도(다른 것들은) 통과하지만 실서비스 로그인이 전멸한다.
 */
class WalletLoginMessageTests {

    private static final String CHECKSUM = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    @Test
    void matchesTheFrontendContractCharacterForCharacter() {
        String expected = "SAEROI 로그인 요청\n"
                + "\n"
                + "지갑 주소: " + CHECKSUM + "\n"
                + "Nonce: " + NONCE + "\n"
                + "\n"
                + "이 서명은 로그인 용도로만 사용되며 자산이 이동하지 않습니다.";

        assertThat(WalletLoginMessage.build(CHECKSUM, NONCE)).isEqualTo(expected);
    }

    @Test
    void usesLineFeedOnlyNeverCarriageReturn() {
        // Windows 에서 편집하다 CRLF 가 섞이면 서명이 전부 어긋난다.
        assertThat(WalletLoginMessage.build(CHECKSUM, NONCE)).doesNotContain("\r");
    }

    @Test
    void utf8ByteLengthIsStable() {
        // 소스 인코딩이 UTF-8 에서 벗어나면 한글 바이트가 달라진다. 문자열 비교만으로는
        // 그 회귀가 드러나지 않을 수 있어 바이트 길이까지 고정한다.
        //
        // 이 숫자(210)가 EIP-191 프리픽스에 들어가는 길이다. 문자 수는 140 이라
        // String.length() 로 프리픽스를 만들면 정확히 여기서 어긋난다 — 70바이트 차이다.
        String message = WalletLoginMessage.build(CHECKSUM, NONCE);

        assertThat(message.getBytes(StandardCharsets.UTF_8)).hasSize(210);
        assertThat(message.length()).isEqualTo(140);
    }

    @Test
    void hasSixLinesWithTwoBlankSeparators() {
        String[] lines = WalletLoginMessage.build(CHECKSUM, NONCE).split("\n", -1);

        assertThat(lines).hasSize(6);
        assertThat(lines[1]).isEmpty();
        assertThat(lines[4]).isEmpty();
    }
}
