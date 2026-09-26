package com.ssafy.upcycle.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@code eth_getTransactionReceipt} 응답을 흉내 낸다.
 *
 * <p>{@code ChainTransactionRecorder} 가 해시를 체인에서 확인한 뒤에만 기록하게 되면서,
 * 온체인 연동 테스트는 <b>체인이 무엇을 답하는지</b>를 말해 줘야 한다. 그 전에는 해시를
 * 확인하지 않았기 때문에 아무 문자열이나 통했고, 그것이 2026-09-21 에 발견된 위조 가능
 * 문제의 원인이었다.
 *
 * <p>여기서 만드는 것은 기록에 필요한 최소 필드뿐이다 — {@code status}·{@code to}·
 * {@code from}·{@code blockNumber}. 이벤트 로그까지 보는 검증은
 * {@code ProcessingReceiptVerifier} 쪽 테스트가 따로 다룬다.
 */
public final class FakeReceipt {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeReceipt() {}

    /** 성공한 트랜잭션. {@code to} 컨트랙트로 {@code from} 지갑이 보낸 것. */
    public static JsonNode success(String to, String from) {
        return success(to, from, 1L);
    }

    public static JsonNode success(String to, String from, long blockNumber) {
        return receipt("0x1", to, from, blockNumber);
    }

    /** 리버트된 트랜잭션. 해시는 실재하지만 아무 일도 일어나지 않았다. */
    public static JsonNode reverted(String to, String from) {
        return receipt("0x0", to, from, 1L);
    }

    private static ObjectNode receipt(String status, String to, String from, long blockNumber) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("status", status);
        node.put("to", to);
        node.put("from", from);
        node.put("blockNumber", "0x" + Long.toHexString(blockNumber));
        node.putArray("logs");
        return node;
    }
}
