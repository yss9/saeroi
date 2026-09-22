package com.ssafy.upcycle.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.service.ChainProcessingStatusReader;
import org.junit.jupiter.api.Test;

/**
 * 실제 체인에서 가공 작업 상태를 읽는다. <b>디코딩이 맞는지 확인하는 유일한 방법이다.</b>
 *
 * <p>{@code getJob} 이 돌려주는 {@code Job} 은 정적 필드만 든 튜플이다 — 주소 2개, 해시 3개,
 * 상태 1개, 시각 2개. web3j 의 {@code StaticStruct} 로 받으면 "Zero length fixed array is
 * invalid type" 으로 터진다. 주문 조회에서 실측으로 겪은 함정이고, <b>단위 테스트로는 드러나지
 * 않았다</b> — 실패하면 조용히 빈 값을 돌려주기 때문이다. 그 조용한 실패가 수령 서명 판정과
 * 발송 가드를 통째로 무력화했다.
 *
 * <p>가드는 "읽을 수 없다" 를 통과로 취급하므로, 디코딩이 깨지면 <b>가드가 있어도 없는 것과
 * 같아진다.</b> 그래서 여기서 한 번은 진짜로 읽어 봐야 한다.
 *
 * <p>네트워크가 필요하므로 설정이 없으면 건너뛴다. CI 에서는 돌지 않는다.
 */
class ChainProcessingReadProbeTests {

    @Test
    void 체인에서_가공_작업_상태를_읽는다() {
        BlockchainProperties properties = new BlockchainProperties(
                31221L,
                "https://rpc.ssafy-blockchain.com",
                "0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66",
                "0x53Fc91Be37720Fcb051004B7aab73639CA266853",
                "0x09C809D42eae5e7281da7b03e0a16bd5528f2b47",
                null);
        ChainProcessingStatusReader reader = new ChainProcessingStatusReader(properties);

        assumeTrue(reader.canRead(), "체인 설정이 없으면 건너뛴다");

        // 없는 작업이라도 0(NONE)이 나오면 디코딩은 성공한 것이다. 실패의 모양은 빈 값이다.
        var status = reader.status(1L);

        assertThat(status)
                .as("디코딩이 실패하면 빈 값이 온다 — 그게 조용한 실패의 모양이었다")
                .isPresent();
        assertThat(status.get()).isBetween(0, 12);

        // 진행됐던 작업도 읽는다. 값을 출력해 눈으로 확인한다.
        for (long id : new long[] {1L, 4L}) {
            System.out.println("[probe] processing job " + id + " status = " + reader.status(id)
                    + " shipped=" + reader.alreadyShipped(id));
        }
    }
}
