package com.ssafy.upcycle.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.blockchain.service.BlockchainJsonRpcClient;
import com.ssafy.upcycle.blockchain.service.ProcessingChainTxFinder;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 가공 작업의 한 단계가 체인에 남긴 거래를 찾는다. 화면이 해시를 잃었을 때 기록을 따라가게 한다.
 *
 * <p>찾은 거래가 <b>하나일 때만</b> 돌려준다. 한 작업의 한 단계는 한 번만 일어나므로 둘 이상은
 * 설명할 수 없는 상태이고, 그중 하나를 골랐다가 엉뚱한 소재가 발행될 수 있다.
 *
 * <p><b>작업이 체인에 등록된 블록부터 찾는다.</b> 0번 블록부터 찾다가 공용 체인의 1,100만 블록을
 * 노드가 10초 안에 훑지 못해 매번 시간 초과가 났고, 업체가 수령 서명을 마친 작업이 "발송됨" 에
 * 멈췄다.
 */
class ProcessingChainTxFinderTests {

    private static final String REGISTRY = "0x00000000000000000000000000000000000000c1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long LATEST = 11_360_295;

    /** 물어본 구간들. {from, to}. */
    private final List<long[]> ranges = new ArrayList<>();
    private final List<List<String>> asked = new ArrayList<>();
    private JsonNode answer;
    private BlockchainTransactionRepository transactions;
    private ProcessingChainTxFinder finder;

    @BeforeEach
    void setUp() throws Exception {
        BlockchainProperties properties = mock(BlockchainProperties.class);
        when(properties.processingRegistryAddress()).thenReturn(REGISTRY);
        transactions = mock(BlockchainTransactionRepository.class);
        when(transactions.findFirstByReferenceTypeAndReferenceIdAndFunctionNameAndStatusOrderByConfirmedAtDesc(
                        any(), anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        answer = logArray();
        BlockchainJsonRpcClient rpc = new BlockchainJsonRpcClient() {
            @Override
            public JsonNode transactionReceipt(String txHash) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long latestBlockNumber() {
                return LATEST;
            }

            @Override
            public JsonNode logs(String address, List<String> topics, long fromBlock, long toBlock) {
                assertThat(address).isEqualTo(REGISTRY);
                asked.add(topics);
                ranges.add(new long[] {fromBlock, toBlock});
                // 답은 첫 구간에만 싣는다. 구간마다 같은 로그를 돌려주면 한 거래가 여러 번 세진다.
                return ranges.size() == 1 ? answer : logArray();
            }
        };
        finder = new ProcessingChainTxFinder(rpc, properties, transactions);
    }

    @Test
    void 그_단계의_이벤트를_작업_번호로_찾아_거래를_돌려준다() throws Exception {
        answer = logArray("{\"transactionHash\":\"0xABC\"}");

        assertThat(finder.find(9L, 31, Action.RECEIVE)).contains("0xabc");
        // 이벤트 서명과 작업 번호(32바이트)로 거른다.
        assertThat(asked.getFirst()).containsExactly(
                Action.RECEIVE.topic(), "0x" + "0".repeat(62) + "1f");
    }

    @Test
    void 작업이_체인에_등록된_블록부터_최신_블록까지만_찾는다() {
        givenRegisteredAt(9L, 11_360_245L);

        finder.find(9L, 20, Action.RECEIVE);

        assertThat(ranges).hasSize(1);
        assertThat(ranges.getFirst()).containsExactly(11_360_245L, LATEST);
    }

    @Test
    void 구간이_길면_십만_블록씩_나눠_묻는다() {
        givenRegisteredAt(9L, LATEST - 250_000);

        finder.find(9L, 20, Action.APPROVE);

        assertThat(ranges).extracting(range -> range[0]).containsExactly(
                LATEST - 250_000, LATEST - 150_000, LATEST - 50_000);
        assertThat(ranges.getLast()[1]).isEqualTo(LATEST);
        // 이어진 구간 사이에 빠지거나 겹치는 블록이 없다.
        for (int index = 1; index < ranges.size(); index++) {
            assertThat(ranges.get(index)[0]).isEqualTo(ranges.get(index - 1)[1] + 1);
        }
    }

    @Test
    void 등록_기록이_없으면_최근_백만_블록만_본다() {
        finder.find(9L, 20, Action.RECEIVE);

        assertThat(ranges.getFirst()[0]).isEqualTo(LATEST - 1_000_000 + 1);
        assertThat(ranges.getLast()[1]).isEqualTo(LATEST);
        assertThat(ranges).hasSize(10);
    }

    /** 승인은 결과물마다 이벤트가 하나씩이다. 한 거래 안의 여러 로그는 한 건이다. */
    @Test
    void 한_거래의_여러_로그는_한_건이다() throws Exception {
        answer = logArray("{\"transactionHash\":\"0xabc\"}", "{\"transactionHash\":\"0xabc\"}");

        assertThat(finder.find(9L, 31, Action.APPROVE)).contains("0xabc");
    }

    @Test
    void 거래가_둘이면_고르지_않는다() throws Exception {
        answer = logArray("{\"transactionHash\":\"0xabc\"}", "{\"transactionHash\":\"0xdef\"}");

        assertThat(finder.find(9L, 31, Action.RECEIVE)).isEmpty();
    }

    @Test
    void 체인_재정렬로_빠진_로그는_없는_것이다() throws Exception {
        answer = logArray("{\"transactionHash\":\"0xabc\",\"removed\":true}");

        assertThat(finder.find(9L, 31, Action.RECEIVE)).isEmpty();
    }

    @Test
    void 기록이_없으면_비어_있다() {
        assertThat(finder.find(9L, 31, Action.RECEIVE)).isEmpty();
    }

    private void givenRegisteredAt(Long jobId, long block) {
        BlockchainTransaction created = mock(BlockchainTransaction.class);
        when(created.getBlockNumber()).thenReturn(block);
        when(transactions.findFirstByReferenceTypeAndReferenceIdAndFunctionNameAndStatusOrderByConfirmedAtDesc(
                        eq(BlockchainReferenceType.PROCESSING), eq(jobId), eq("createJob"),
                        eq(BlockchainTransactionStatus.SUCCESS)))
                .thenReturn(Optional.of(created));
    }

    private static JsonNode logArray(String... entries) {
        try {
            return JSON.readTree("[" + String.join(",", entries) + "]");
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
