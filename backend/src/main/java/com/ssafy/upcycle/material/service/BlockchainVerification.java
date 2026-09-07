package com.ssafy.upcycle.material.service;

/**
 * 소재가 온체인에서 출처 검증을 받았는지 묻는 창구.
 *
 * <p>{@link TradeVerification} 과 같은 방식이다 — <b>질문은 material 이 선언하고 사실을
 * 가진 쪽이 구현한다.</b> material 이 blockchain 리포지토리를 직접 끌어다 쓰면 참조가
 * 양방향으로 열린다.
 *
 * <p><b>왜 이 단만 온체인을 보는가.</b> 거래 수령·가공 승인·생산 확정은 우리 API 를
 * 반드시 거치는 흐름이라 그 사실이 DB 에 남는다. 그런데 {@code verifySource} 는 최초 등록
 * SUPPLIER 가 "이 출처가 사실이다" 라고 서명하는 <b>독립 행위</b>이고 우리 흐름 어디에도
 * 붙어 있지 않다. 대응하는 DB 사실이 없어 온체인 기록을 봐야 한다.
 *
 * <p>v3 기획은 이 판정용으로 {@code material.signed_at}·{@code signer_wallet} 컬럼을
 * 추가하려 했지만 실제 스키마에 없다. 그 컬럼을 만들지 않기로 했다 — 서명 사실은
 * 온체인에 있고, DB 컬럼을 두면 진실이 둘이 되어 어긋났을 때 판정할 방법이 없다.
 *
 * @see com.ssafy.upcycle.blockchain.service.BlockchainVerificationService
 */
public interface BlockchainVerification {

    /**
     * 이 소재에 대해 {@code verifySource} 가 온체인에서 <b>성공</b>했는가.
     *
     * <p>확정된 트랜잭션만 인정한다. 대기 중이거나 실패한 것을 인정하면 일어나지 않은
     * 검증을 화면에 표시하게 되고, 추적성 화면에서 그건 되돌릴 수 없는 거짓이 된다.
     */
    boolean hasVerifiedSource(Long materialId);
}
