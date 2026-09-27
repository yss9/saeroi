package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

@Service
@RequiredArgsConstructor
public class ProcessingReceiptVerifier {
    public enum Action {
        RECEIVE("ProcessingInputCustodyTransferred(uint256,uint256,uint256,uint256,uint256,address,address)"),
        RETURN_SHIP("ProcessingReturnShipped(uint256,address,bytes32,uint256)"),
        APPROVE("MaterialProcessed(uint256,uint256,uint256,uint256,address,address,uint256)"),
        REJECTED_RETURN("ProcessingRejectedInputReturned(uint256,address,address,bytes32,uint256)");

        private final String topic;
        Action(String signature) { this.topic = Hash.sha3String(signature).toLowerCase(); }

        /** 이 단계가 남기는 이벤트의 첫 토픽. 해시 없이 체인에서 그 거래를 찾을 때 쓴다. */
        public String topic() { return topic; }
    }

    public record VerifiedReceipt(
            long blockNumber, Long processorLockId, String evidenceHash, List<Long> outputTokenIds) {}

    private final BlockchainJsonRpcClient rpc;
    private final BlockchainProperties properties;

    public VerifiedReceipt verifyReceipt(
            String txHash,
            long chainJobId,
            String expectedSigner,
            Action action,
            String expectedEvidenceHash,
            int expectedEventCount) {
        JsonNode receipt = rpc.transactionReceipt(txHash);
        requireEquals("0x1", receipt.path("status").asText());
        requireEquals(properties.processingRegistryAddress(), receipt.path("to").asText());
        requireEquals(expectedSigner, receipt.path("from").asText());

        List<JsonNode> matching = new ArrayList<>();
        for (JsonNode log : receipt.path("logs")) {
            if (!same(properties.processingRegistryAddress(), log.path("address").asText())) continue;
            JsonNode topics = log.path("topics");
            if (!topics.isArray() || topics.size() < 2) continue;
            if (!same(action.topic, topics.get(0).asText())) continue;
            if (unsignedLong(topics.get(1).asText()) != chainJobId) continue;
            matching.add(log);
        }
        if (matching.size() != expectedEventCount) throw invalid();

        Long processorLockId = null;
        String evidenceHash = null;
        List<Long> outputTokenIds = new ArrayList<>();
        if (action == Action.RECEIVE) {
            processorLockId = dataWordLong(matching.getFirst(), 1);
        } else if (action == Action.RETURN_SHIP || action == Action.REJECTED_RETURN) {
            evidenceHash = dataWord(matching.getFirst(), 0);
            requireEquals(expectedEvidenceHash, evidenceHash);
        } else if (action == Action.APPROVE) {
            for (JsonNode log : matching) {
                JsonNode topics = log.path("topics");
                if (topics.size() < 4) throw invalid();
                outputTokenIds.add(unsignedLong(topics.get(3).asText()));
            }
        }
        return new VerifiedReceipt(hexLong(receipt.path("blockNumber").asText()), processorLockId,
                evidenceHash, List.copyOf(outputTokenIds));
    }

    private static long dataWordLong(JsonNode log, int index) {
        return unsignedLong("0x" + dataWord(log, index).substring(2));
    }

    private static String dataWord(JsonNode log, int index) {
        String data = log.path("data").asText();
        if (!data.startsWith("0x") || data.length() < 2 + (index + 1) * 64) throw invalid();
        int start = 2 + index * 64;
        return "0x" + data.substring(start, start + 64).toLowerCase();
    }

    private static long unsignedLong(String value) {
        try {
            String raw = value.startsWith("0x") ? value.substring(2) : value;
            return new BigInteger(raw, 16).longValueExact();
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private static long hexLong(String value) { return unsignedLong(value); }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static void requireEquals(String expected, String actual) {
        if (!same(expected, actual)) throw invalid();
    }

    private static BusinessException invalid() {
        return new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
    }
}
