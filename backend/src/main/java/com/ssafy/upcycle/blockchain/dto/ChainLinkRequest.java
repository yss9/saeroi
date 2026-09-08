package com.ssafy.upcycle.blockchain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 화면이 지갑으로 보낸 트랜잭션이 확정된 뒤, 그 결과를 서버에 알리는 요청.
 *
 * <p>세 경로(소재 발행·판매 등록·주문 생성)가 같은 모양이라 하나로 쓴다. 무엇에 붙일지는
 * 경로의 {@code {id}} 가 정하므로 바디에 대상이 없다.
 *
 * <p><b>체인 ID 와 컨트랙트 주소를 받지 않는다.</b> 서버가 {@code BlockchainProperties} 로
 * 들고 있다 — 화면이 보내면 자기가 만든 컨트랙트 주소로 "온체인에 등록됨" 을 만들 수 있다.
 *
 * @param onChainId 체인이 채번한 번호. 이벤트에서 꺼낸 값이다 (반환값은 영수증에 없다)
 * @param txHash 확정된 트랜잭션 해시. {@code 0x} + 64 hex
 * @param blockNumber 채굴된 블록. 없어도 기록은 성립하므로 선택이다
 */
public record ChainLinkRequest(
        @NotNull @Positive Long onChainId,

        @NotBlank
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "트랜잭션 해시 형식이 올바르지 않습니다.")
        String txHash,

        Long blockNumber) {
}
