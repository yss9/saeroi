package com.ssafy.upcycle.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.dto.ChainEvidenceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainLiveQueryServiceTests {

    @Mock BesuLiveRpcClient rpc;
    private final ObjectMapper mapper = new ObjectMapper();
    private BlockchainLiveQueryService service;

    @BeforeEach
    void setUp() {
        service = new BlockchainLiveQueryService(rpc, new BlockchainProperties(
                31221L, "http://besu.test", null, null, null, null));
    }

    @Test
    void readsTransactionReceiptBlockAndLatestHeightFromRpc() throws Exception {
        String hash = "0x" + "a".repeat(64);
        when(rpc.transaction(hash)).thenReturn(mapper.readTree("""
                {"value":"0x2a","input":"0x1234"}
                """));
        when(rpc.receipt(hash)).thenReturn(mapper.readTree("""
                {
                  "transactionHash":"%s","blockNumber":"0x64","blockHash":"0xblock",
                  "from":"0xfrom","to":"0xto","status":"0x1","gasUsed":"0x5208",
                  "logs":[{},{}]
                }
                """.formatted(hash)));
        when(rpc.latestBlockNumber()).thenReturn(105L);
        when(rpc.block(100L, false)).thenReturn(mapper.readTree("{" +
                "\"timestamp\":\"0x65\"}"));

        ChainEvidenceResponse result = service.findEvidence(hash);

        assertThat(result.blockNumber()).isEqualTo(100L);
        assertThat(result.latestBlock()).isEqualTo(105L);
        assertThat(result.confirmations()).isEqualTo(6L);
        assertThat(result.status()).isEqualTo(1);
        assertThat(result.gasUsed()).isEqualTo("21000");
        assertThat(result.value()).isEqualTo("42");
        assertThat(result.logCount()).isEqualTo(2);
        assertThat(result.chainId()).isEqualTo("0x79f5");
    }
}
