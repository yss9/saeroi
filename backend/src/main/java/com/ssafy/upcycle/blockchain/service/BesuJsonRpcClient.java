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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BesuJsonRpcClient implements BlockchainJsonRpcClient {
    private final BlockchainProperties properties;
    private final ObjectMapper mapper;

    @Override
    public JsonNode transactionReceipt(String txHash) {
        if (!properties.hasRpcUrl()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0", "id", 1, "method", "eth_getTransactionReceipt",
                    "params", java.util.List.of(txHash)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.rpcUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw invalid();
            JsonNode root = mapper.readTree(response.body());
            JsonNode result = root.path("result");
            if (result.isMissingNode() || result.isNull() || !result.isObject()) throw invalid();
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw invalid();
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
    }
}
