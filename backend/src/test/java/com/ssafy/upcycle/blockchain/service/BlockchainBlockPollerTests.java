package com.ssafy.upcycle.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.dto.LiveBlockEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainBlockPollerTests {

    private static final String MATERIAL = "0x1111111111111111111111111111111111111111";

    @Mock BesuLiveRpcClient rpc;
    @Mock BlockchainLiveEventHub hub;

    private final ObjectMapper mapper = new ObjectMapper();
    private BlockchainBlockPoller poller;

    @BeforeEach
    void setUp() {
        BlockchainProperties properties = new BlockchainProperties(
                31221L,
                "http://besu.test",
                MATERIAL,
                "0x2222222222222222222222222222222222222222",
                "0x3333333333333333333333333333333333333333",
                "0x4444444444444444444444444444444444444444");
        poller = new BlockchainBlockPoller(rpc, properties, hub);
    }

    @Test
    void firstPollPublishesLatestBlockAndLabelsProjectTransaction() throws Exception {
        when(rpc.latestBlockNumber()).thenReturn(42L);
        when(rpc.block(42L, true)).thenReturn(mapper.readTree("""
                {
                  "number":"0x2a",
                  "hash":"0xblock",
                  "parentHash":"0xparent",
                  "timestamp":"0x65",
                  "transactions":[{
                    "hash":"0xtx",
                    "from":"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "to":"%s",
                    "value":"0x10",
                    "input":"0x12345678"
                  }]
                }
                """.formatted(MATERIAL)));

        poller.poll();

        ArgumentCaptor<LiveBlockEvent> event = ArgumentCaptor.forClass(LiveBlockEvent.class);
        verify(hub).publish(event.capture());
        verify(hub).heartbeat(42L);
        assertThat(event.getValue().number()).isEqualTo(42L);
        assertThat(event.getValue().projectTransactionCount()).isEqualTo(1);
        assertThat(event.getValue().transactions().getFirst().contractName())
                .isEqualTo("MaterialToken");
        assertThat(event.getValue().transactions().getFirst().value()).isEqualTo("16");
    }

    @Test
    void subsequentPollStartsAfterLastProcessedBlock() throws Exception {
        when(rpc.latestBlockNumber()).thenReturn(100L, 102L);
        when(rpc.block(100L, true)).thenReturn(emptyBlock(100));
        when(rpc.block(101L, true)).thenReturn(emptyBlock(101));
        when(rpc.block(102L, true)).thenReturn(emptyBlock(102));

        poller.poll();
        poller.poll();

        verify(rpc).block(100L, true);
        verify(rpc).block(101L, true);
        verify(rpc).block(102L, true);
    }

    private com.fasterxml.jackson.databind.JsonNode emptyBlock(long number) throws Exception {
        return mapper.readTree("""
                {"number":"0x%s","hash":"0x%s","parentHash":"0xparent","timestamp":"0x65","transactions":[]}
                """.formatted(Long.toHexString(number), number));
    }
}
