package com.ssafy.upcycle.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 프론트가 {@code tx.wait()} 로 채굴만 확인한 직후라, RPC 가 그 영수증을 아직 못 돌려줄
 * 수 있다 — 영구 실패가 아니라 뒤처짐이다. 여기서는 그 뒤처짐을 실제 HTTP 서버로 흉내내
 * 재시도가 정말 회복하는지, 그리고 계속 안 보일 때 정말 포기하는지를 검증한다.
 */
class BesuJsonRpcClientTests {
    private static final String TX = "0x" + "a".repeat(64);
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void recoversWhenReceiptIsNotYetVisibleOnFirstAttempts() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = respondWith(exchange -> {
            String body = calls.incrementAndGet() <= 2
                    ? "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"
                    : "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"status\":\"0x1\"}}";
            write(exchange, body);
        });

        BesuJsonRpcClient client = client(rpcUrl());

        JsonNode receipt = client.transactionReceipt(TX);

        assertThat(receipt.path("status").asText()).isEqualTo("0x1");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterReceiptStaysMissing() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = respondWith(exchange -> {
            calls.incrementAndGet();
            write(exchange, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
        });

        BesuJsonRpcClient client = client(rpcUrl());

        assertThatThrownBy(() -> client.transactionReceipt(TX))
                .isInstanceOf(BusinessException.class);
        assertThat(calls.get()).isEqualTo(5);
    }

    private BesuJsonRpcClient client(String url) {
        return new BesuJsonRpcClient(
                new BlockchainProperties(31221L, url,
                        "0x" + "1".repeat(40), "0x" + "2".repeat(40), "0x" + "3".repeat(40), null),
                mapper);
    }

    private String rpcUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private HttpServer respondWith(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer newServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        newServer.createContext("/", handler);
        newServer.start();
        return newServer;
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
