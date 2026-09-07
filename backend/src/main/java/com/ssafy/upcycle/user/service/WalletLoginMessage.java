package com.ssafy.upcycle.user.service;

/**
 * 사용자가 MetaMask 에서 실제로 보게 되는 서명 문구.
 *
 * <p><b>Frontend 의 {@code buildLoginMessage()} 와 한 글자도 달라선 안 된다.</b>
 * 서명 검증은 이 문자열의 UTF-8 바이트를 그대로 해싱해 주소를 복원하므로, 공백 하나
 * 줄바꿈 하나만 어긋나도 복원된 주소가 완전히 달라진다. 그때 나타나는 증상은
 * "형식 오류" 가 아니라 <b>모든 로그인이 SIGNATURE_MISMATCH 로 실패</b>하는 것이라
 * 원인을 찾기 어렵다. 바꿔야 한다면 Frontend 와 같은 시점에 바꾸고 양쪽 테스트를 함께 고친다.
 *
 * <p>문구가 사람이 읽을 수 있어야 하는 이유도 있다. MetaMask 는 이 문자열을 그대로 보여주며,
 * 사용자는 그것만 보고 무엇에 서명하는지 판단한다. 자산이 움직이지 않는다는 사실을 명시한다.
 *
 * <p>이 파일은 반드시 UTF-8 로 저장되어야 한다. Boot parent POM 이
 * {@code project.build.sourceEncoding=UTF-8} 을 강제하므로 지금은 보장되지만,
 * 인코딩이 어긋나면 한글 바이트가 달라져 역시 전량 실패한다.
 * {@code WalletLoginMessageTests} 가 바이트 길이까지 고정해 그 회귀를 잡는다.
 */
public final class WalletLoginMessage {

    private static final String TITLE = "SAEROI 로그인 요청";
    private static final String ADDRESS_LABEL = "지갑 주소: ";
    private static final String NONCE_LABEL = "Nonce: ";
    private static final String DISCLAIMER = "이 서명은 로그인 용도로만 사용되며 자산이 이동하지 않습니다.";

    private WalletLoginMessage() {
    }

    /**
     * @param checksummedAddress EIP-55 체크섬 형태의 주소. 소문자 주소를 넣으면 지갑이 서명한
     *     문자열과 달라져 검증에 실패한다 — 호출부가 {@code Keys.toChecksumAddress} 를 거쳐야 한다.
     */
    public static String build(String checksummedAddress, String nonce) {
        return String.join(
                "\n",
                TITLE,
                "",
                ADDRESS_LABEL + checksummedAddress,
                NONCE_LABEL + nonce,
                "",
                DISCLAIMER);
    }
}
