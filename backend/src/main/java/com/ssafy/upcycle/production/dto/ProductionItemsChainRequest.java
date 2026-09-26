package com.ssafy.upcycle.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * {@code linkProductItems} 결과를 알리는 요청. <b>온체인 4단계 중 마지막이다.</b>
 *
 * <p><b>개체 번호를 받지 않는다.</b> 컨트랙트가 받는 {@code productItemIds[]} 는 우리
 * {@code product_item.id} 를 그대로 쓴다 — 컨트랙트는 그 번호가 양수이고 중복이 없고 개수가
 * {@code productQuantity} 와 같은지만 본다. 온체인에서 따로 채번하지 않으므로 서버가 이미
 * 아는 값이고, 요청으로 받으면 <b>DB 에 없는 번호를 체인에 올려 놓고 올렸다고 보고</b>할 수
 * 있다.
 *
 * <p>화면이 보낼 배열은 {@code GET /{id}/items} 의 {@code productItemId} 를
 * {@code serialNumber} 순서 그대로 쓰면 된다.
 */
public record ProductionItemsChainRequest(

        @NotBlank(message = "트랜잭션 해시는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "트랜잭션 해시 형식이 올바르지 않습니다.")
        String txHash,

        Long blockNumber) {
}
