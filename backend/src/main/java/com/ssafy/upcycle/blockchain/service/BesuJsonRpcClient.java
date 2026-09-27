package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 프론트는 {@code tx.wait()} 로 채굴만 확인하고 바로 이 조회를 부른다. RPC 가 노드
 * 여러 대로 분산돼 있으면, 방금 채굴된 영수증이 이 노드에는 아직 안 보일 수 있다 —
 * 영구 실패가 아니라 뒤처짐이다. 재시도 없이 한 번에 포기하면, 그 순간 온체인은 이미
 * 성공했는데 서버만 실패로 기록해 사용자가 되돌릴 방법이 없는 상태(온체인은 상태가
 * 바뀌어 같은 서명을 다시 보내면 컨트랙트가 되돌린다)에 빠진다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BesuJsonRpcClient implements BlockchainJsonRpcClient {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RETRY_DELAY = Duration.ofMillis(400);

    private final BlockchainProperties properties;
    private final ObjectMapper mapper;

    @Override
    public JsonNode transactionReceipt(String txHash) {
        if (!properties.hasRpcUrl()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode result = fetchReceipt(txHash);
                if (result != null) return result;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw invalid(txHash, MAX_ATTEMPTS, exception);
            } catch (Exception exception) {
                lastFailure = exception;
            }
            if (attempt < MAX_ATTEMPTS) sleep();
        }
        throw invalid(txHash, MAX_ATTEMPTS, lastFailure);
    }

    /** 영수증이 아직 안 보이면(result 없음/null) 예외가 아니라 {@code null} 을 돌려준다 — 재시도할 신호다. */
    private JsonNode fetchReceipt(String txHash) throws Exception {
        JsonNode result = call("eth_getTransactionReceipt", java.util.List.of(txHash));
        if (result.isMissingNode() || result.isNull() || !result.isObject()) return null;
        return result;
    }

    /**
     * 주어진 블록 구간의 로그를 찾는다. 구간은 호출하는 쪽이 좁혀 준다
     * ({@link BlockchainJsonRpcClient#logs} 참고 — 0번 블록부터 찾다가 시간 초과가 났다).
     *
     * <p>영수증과 달리 재시도하지 않는다. 방금 채굴된 거래를 찾는 경로가 아니라, 한참 전에 들어간
     * 서명을 따라잡는 경로라서 노드 사이의 뒤처짐이 문제되지 않는다.
     */
    @Override
    public JsonNode logs(String address, java.util.List<String> topics, long fromBlock, long toBlock) {
        if (!properties.hasRpcUrl()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
        try {
            JsonNode result = call("eth_getLogs", java.util.List.of(Map.of(
                    "address", address,
                    "topics", topics,
                    "fromBlock", "0x" + Long.toHexString(fromBlock),
                    "toBlock", "0x" + Long.toHexString(toBlock))));
            if (!result.isArray()) {
                throw new IllegalStateException("eth_getLogs returned no array");
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw logsFailed(address, exception);
        } catch (Exception exception) {
            throw logsFailed(address, exception);
        }
    }

    @Override
    public long latestBlockNumber() {
        if (!properties.hasRpcUrl()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
        try {
            String hex = call("eth_blockNumber", java.util.List.of()).asText("");
            if (!hex.startsWith("0x")) {
                throw new IllegalStateException("eth_blockNumber returned no number");
            }
            return Long.parseLong(hex.substring(2), 16);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw blockNumberFailed(exception);
        } catch (Exception exception) {
            throw blockNumberFailed(exception);
        }
    }

    private JsonNode call(String method, Object params) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", method, "params", params));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.rpcUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("RPC HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body()).path("result");
    }

    private static BusinessException logsFailed(String address, Exception cause) {
        log.warn("Chain log lookup failed for contract {}", address, cause);
        return new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
    }

    private static BusinessException blockNumberFailed(Exception cause) {
        log.warn("Chain block number lookup failed", cause);
        return new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static BusinessException invalid(String txHash, int attempts, Exception cause) {
        log.warn("Processing receipt lookup failed for tx {} after {} attempts", txHash, attempts, cause);
        return new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
    }
}
