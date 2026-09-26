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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 실시간 대시보드 전용 읽기 RPC. 트랜잭션을 만들거나 서명하는 기능은 갖지 않는다. */
@Component
@RequiredArgsConstructor
public class BesuLiveRpcClient {

    private final BlockchainProperties properties;
    private final ObjectMapper mapper;
    private final AtomicLong requestId = new AtomicLong();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public long latestBlockNumber() {
        return hexLong(required(call("eth_blockNumber", List.of())));
    }

    public JsonNode block(long number, boolean fullTransactions) {
        return required(call("eth_getBlockByNumber", List.of(hex(number), fullTransactions)));
    }

    public JsonNode transaction(String txHash) {
        return required(call("eth_getTransactionByHash", List.of(txHash)));
    }

    public JsonNode receipt(String txHash) {
        return required(call("eth_getTransactionReceipt", List.of(txHash)));
    }

    private JsonNode call(String method, List<?> params) {
        if (!properties.hasRpcUrl()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0",
                    "id", requestId.incrementAndGet(),
                    "method", method,
                    "params", params));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.rpcUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Besu RPC HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("Besu RPC error: " + root.path("error"));
            }
            JsonNode result = root.path("result");
            return result.isMissingNode() || result.isNull() ? null : result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_UNAVAILABLE);
        } catch (Exception exception) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_UNAVAILABLE);
        }
    }

    private static JsonNode required(JsonNode result) {
        if (result == null) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_TRANSACTION_NOT_FOUND);
        }
        return result;
    }

    static long hexLong(JsonNode value) {
        return hexLong(value.asText());
    }

    static long hexLong(String value) {
        if (value == null || !value.matches("^0x[0-9a-fA-F]+$")) {
            throw new IllegalArgumentException("invalid RPC hex number");
        }
        return Long.parseUnsignedLong(value.substring(2), 16);
    }

    private static String hex(long value) {
        return "0x" + Long.toHexString(value);
    }
}
