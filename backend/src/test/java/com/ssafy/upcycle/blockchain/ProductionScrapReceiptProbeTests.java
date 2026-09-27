package com.ssafy.upcycle.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.service.BesuJsonRpcClient;
import com.ssafy.upcycle.blockchain.service.ProductionScrapReceiptReader;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 실제 생산 확정 영수증에서 <b>자투리 토큰 번호</b>를 읽는다.
 *
 * <p>{@code ProductionScrapCreated} 는 세 값이 모두 indexed 라 {@code topics} 에 들어 있다.
 * 자리를 하나만 틀려도 엉뚱한 번호를 소재에 붙이게 되고, 한 번 붙으면 되돌릴 수 없다
 * ({@code Material.linkOnChain} 이 재배정을 막는다). 그래서 실제 영수증으로 한 번은
 * 확인해야 한다.
 *
 * <p>읽기 실패의 모양이 "빈 map" 이라 단위 테스트로는 잡히지 않는다 — 조용히 아무것도 안
 * 붙이고 지나간다. {@code ChainOrderReadProbeTests} 와 같은 이유의 프로브다.
 *
 * <p>네트워크가 필요하므로 설정이 없으면 건너뛴다. CI 에서는 돌지 않는다.
 */
class ProductionScrapReceiptProbeTests {

    /** 배치 5(2026 실크 열쇠고리 1차)의 confirmProduction. 자투리 500g 이 발행됐다. */
    private static final String CONFIRM_TX =
            "0xda4d8dff1866eb91259207a0461da1c98604335848adac3bc473da96b1e21e08";
    private static final long CHAIN_BATCH_ID = 11L;

    @Test
    void 확정_영수증에서_자투리_토큰을_읽는다() {
        BlockchainProperties properties = new BlockchainProperties(
                31221L,
                "https://rpc.ssafy-blockchain.com",
                "0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66",
                "0x53Fc91Be37720Fcb051004B7aab73639CA266853",
                "0x09C809D42eae5e7281da7b03e0a16bd5528f2b47",
                "0x65B4D067E929a7Ce64E8d5896bD8D4e0efE6DD3a");
        assumeTrue(properties.hasProductionRegistry(), "체인 설정이 없으면 건너뛴다");

        // **@SpringBootTest 를 쓰지 않는다.** 테스트 컨텍스트는 RPC 를 스텁으로 갈아끼운다
        // (ScriptedReceiptClient) — 그러면 실제 체인이 아니라 각본을 읽어 늘 빈 결과가 온다.
        ProductionScrapReceiptReader reader = new ProductionScrapReceiptReader(
                new BesuJsonRpcClient(properties, new ObjectMapper()), properties);
        Map<Long, Long> scrapByParent = reader.scrapTokensOf(CONFIRM_TX, CHAIN_BATCH_ID);

        System.out.println("[probe] scrap tokens = " + scrapByParent);
        assertThat(scrapByParent)
                .as("자투리 500g 이 발행된 배치다 — 비어 있으면 이벤트를 못 읽은 것이다")
                .isNotEmpty();
        // 부모는 실크 원단(토큰 65)이고 자투리는 그보다 뒤에 발행된 번호다.
        assertThat(scrapByParent).containsKey(65L);
        assertThat(scrapByParent.get(65L)).isGreaterThan(65L);
    }
}
