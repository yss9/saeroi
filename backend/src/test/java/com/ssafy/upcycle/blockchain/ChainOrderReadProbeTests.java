package com.ssafy.upcycle.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import org.junit.jupiter.api.Test;

/**
 * 실제 체인에서 주문 상태를 읽는다. <b>디코딩이 맞는지 확인하는 유일한 방법이다.</b>
 *
 * <p>예전에는 web3j 의 {@code StaticStruct} 로 튜플을 받았는데 "Zero length fixed array is
 * invalid type" 으로 터졌다. 단위 테스트로는 드러나지 않았다 — 이 경로가 호출되는 자리가
 * 없었고, 실패하면 조용히 빈 값을 돌려줬기 때문이다. 그 조용한 실패가 나중에 수령 서명
 * 판정과 발송 가드를 통째로 막았다.
 *
 * <p>네트워크가 필요하므로 설정이 없으면 건너뛴다. CI 에서는 돌지 않는다.
 */
class ChainOrderReadProbeTests {

    @Test
    void 체인에서_주문_상태를_읽는다() {
        BlockchainProperties properties = new BlockchainProperties(
                31221L,
                "https://rpc.ssafy-blockchain.com",
                "0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66",
                "0x53Fc91Be37720Fcb051004B7aab73639CA266853",
                "0x09C809D42eae5e7281da7b03e0a16bd5528f2b47",
                null);
        ChainOrderStatusReader reader = new ChainOrderStatusReader(properties);

        assumeTrue(reader.canRead(), "체인 설정이 없으면 건너뛴다");

        // 없는 주문이라도 0(NONE)이 나오면 디코딩은 성공한 것이다. 실패의 모양은 빈 값이다.
        var status = reader.status(1L);

        assertThat(status)
                .as("디코딩이 실패하면 빈 값이 온다 — 그게 조용한 실패의 모양이었다")
                .isPresent();
        assertThat(status.get()).isBetween(0, 6);

        // 실제로 진행 중인 주문도 읽는다. 값을 출력해 눈으로 확인한다.
        System.out.println("[probe] order 41 status = " + reader.status(41L));
        System.out.println("[probe] order 41 alreadyShipped = " + reader.alreadyShipped(41L));
        System.out.println("[probe] order 41 alreadyReceived = " + reader.alreadyReceived(41L));
    }
}
