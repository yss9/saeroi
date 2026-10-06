package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@FunctionalInterface
public interface BlockchainJsonRpcClient {
    JsonNode transactionReceipt(String txHash);

    /** 한 번만 조회한다. 아직 채굴되지 않았으면 {@code null}; 워커가 다음 실행에서 다시 본다. */
    default JsonNode transactionReceiptOrNull(String txHash) {
        return transactionReceipt(txHash);
    }

    /**
     * 이 컨트랙트가 이 토픽들로 <b>주어진 블록 구간에</b> 남긴 로그({@code eth_getLogs}). 결과는 로그
     * 배열이다.
     *
     * <p><b>거래 해시를 모를 때 체인에서 그 거래를 찾는 데 쓴다.</b> 서명은 체인에 들어갔는데 서버
     * 호출이 실패하면 화면은 해시를 잃는다. 다시 서명하려 하면 체인이 이미 그 단계를 지나
     * 되돌리므로, 서버가 체인에서 그 거래를 찾아야 기록이 따라갈 수 있다
     * ({@code ProcessingChainTxFinder}).
     *
     * <p><b>구간을 반드시 받는다.</b> 처음에는 0번 블록부터 찾았는데, 공용 SSAFY 체인은 10초마다
     * 블록이 쌓여 1,100만 블록이 넘는다. 노드가 10초 안에 답하지 못해 매번 시간 초과가 났고, 업체가
     * 수령 서명을 마친 가공 작업 두 건이 "발송됨" 에 멈췄다. 10만 블록은 0.5초 안에 답한다.
     *
     * <p>기본 구현은 없다. 영수증만 확인하는 구현(테스트용 포함)이 이것까지 흉내 낼 필요가 없게 한다.
     *
     * @param topics 순서대로 맞춰 볼 토픽. 첫 번째가 이벤트 서명의 해시다
     * @param fromBlock 구간의 첫 블록(포함)
     * @param toBlock 구간의 끝 블록(포함)
     */
    default JsonNode logs(String address, List<String> topics, long fromBlock, long toBlock) {
        throw new UnsupportedOperationException("eth_getLogs is not supported by this client");
    }

    /** 지금 가장 최근 블록 번호({@code eth_blockNumber}). 로그를 찾을 구간의 끝이다. */
    default long latestBlockNumber() {
        throw new UnsupportedOperationException("eth_blockNumber is not supported by this client");
    }
}
