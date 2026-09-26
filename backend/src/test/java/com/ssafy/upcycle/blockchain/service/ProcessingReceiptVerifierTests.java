package com.ssafy.upcycle.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;

class ProcessingReceiptVerifierTests {
    private static final String REGISTRY = "0x3333333333333333333333333333333333333333";
    private static final String PROCESSOR = "0x6666666666666666666666666666666666666666";
    private static final String BRAND = "0x5555555555555555555555555555555555555555";
    private static final String TX = "0x" + "a".repeat(64);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractsReplacementLockFromConfirmedReceiptEvent() throws Exception {
        var verifier = verifier(receipt(PROCESSOR, List.of(log(
                "ProcessingInputCustodyTransferred(uint256,uint256,uint256,uint256,uint256,address,address)",
                List.of(word(41), word(7)),
                words(1001, 2001, 300, BRAND, PROCESSOR)))));

        var verified = verifier.verifyReceipt(TX, 41L, PROCESSOR,
                ProcessingReceiptVerifier.Action.RECEIVE, null, 1);

        assertThat(verified.blockNumber()).isEqualTo(26L);
        assertThat(verified.processorLockId()).isEqualTo(2001L);
    }

    @Test
    void rejectsReceiptFromAnotherRegistry() throws Exception {
        var node = receipt(PROCESSOR, List.of(log(
                "ProcessingReceived(uint256,address,uint256)",
                List.of(word(41), addressWord(PROCESSOR)), words(1))));
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("logs").get(0))
                .put("address", "0x4444444444444444444444444444444444444444");

        assertThatThrownBy(() -> verifier(node).verifyReceipt(TX, 41L, PROCESSOR,
                ProcessingReceiptVerifier.Action.RECEIVE, null, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void verifiesUploadedEvidenceHashInReturnShipmentEvent() throws Exception {
        String evidenceHash = "0x" + "b".repeat(64);
        var verifier = verifier(receipt(PROCESSOR, List.of(log(
                "ProcessingReturnShipped(uint256,address,bytes32,uint256)",
                List.of(word(41), addressWord(PROCESSOR)), words(evidenceHash, 1)))));

        assertThat(verifier.verifyReceipt(TX, 41L, PROCESSOR,
                ProcessingReceiptVerifier.Action.RETURN_SHIP, evidenceHash, 1).evidenceHash())
                .isEqualTo(evidenceHash);
    }

    @Test
    void extractsEveryChildTokenFromApprovalReceipt() throws Exception {
        var verifier = verifier(receipt(BRAND, List.of(
                log("MaterialProcessed(uint256,uint256,uint256,uint256,address,address,uint256)",
                        List.of(word(41), word(7), word(91)), words(200, PROCESSOR, BRAND, 1)),
                log("MaterialProcessed(uint256,uint256,uint256,uint256,address,address,uint256)",
                        List.of(word(41), word(7), word(92)), words(50, PROCESSOR, BRAND, 1)))));

        assertThat(verifier.verifyReceipt(TX, 41L, BRAND,
                ProcessingReceiptVerifier.Action.APPROVE, null, 2).outputTokenIds())
                .containsExactly(91L, 92L);
    }

    @Test
    void rejectsFailedTransactionReceipt() throws Exception {
        var failed = receipt(PROCESSOR, List.of());
        ((com.fasterxml.jackson.databind.node.ObjectNode) failed).put("status", "0x0");

        assertThatThrownBy(() -> verifier(failed).verifyReceipt(TX, 41L, PROCESSOR,
                ProcessingReceiptVerifier.Action.RECEIVE, null, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsTransactionSignedByAnotherWallet() throws Exception {
        var receipt = receipt(BRAND, List.of(log(
                "ProcessingReturnShipped(uint256,address,bytes32,uint256)",
                List.of(word(41), addressWord(PROCESSOR)), words("0x" + "b".repeat(64), 1))));

        assertThatThrownBy(() -> verifier(receipt).verifyReceipt(TX, 41L, PROCESSOR,
                ProcessingReceiptVerifier.Action.RETURN_SHIP, "0x" + "b".repeat(64), 1))
                .isInstanceOf(BusinessException.class);
    }

    private ProcessingReceiptVerifier verifier(com.fasterxml.jackson.databind.JsonNode receipt) {
        BlockchainJsonRpcClient client = ignored -> receipt;
        return new ProcessingReceiptVerifier(client, new BlockchainProperties(
                31337L, "http://localhost:8545",
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222", REGISTRY,
                "0x4444444444444444444444444444444444444444"));
    }

    private com.fasterxml.jackson.databind.JsonNode receipt(
            String from, List<com.fasterxml.jackson.databind.JsonNode> logs) throws Exception {
        var root = mapper.createObjectNode();
        root.put("status", "0x1");
        root.put("blockNumber", "0x1a");
        root.put("from", from);
        root.put("to", REGISTRY);
        root.set("logs", mapper.valueToTree(logs));
        return root;
    }

    private com.fasterxml.jackson.databind.JsonNode log(
            String signature, List<String> indexed, String data) {
        var node = mapper.createObjectNode();
        node.put("address", REGISTRY);
        var topics = mapper.createArrayNode().add(Hash.sha3String(signature));
        indexed.forEach(topics::add);
        node.set("topics", topics);
        node.put("data", "0x" + data);
        return node;
    }

    private static String words(Object... values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            String raw = value instanceof String text && text.startsWith("0x") ? text.substring(2)
                    : value instanceof String text ? text.substring(2) : Long.toHexString(((Number) value).longValue());
            result.append("0".repeat(64 - raw.length())).append(raw.toLowerCase());
        }
        return result.toString();
    }

    private static String word(long value) { return "0x" + words(value); }
    private static String addressWord(String value) { return "0x" + words(value); }
}
