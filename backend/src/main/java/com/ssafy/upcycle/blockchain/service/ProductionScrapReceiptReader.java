package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;

/**
 * 생산 확정 영수증에서 <b>발행된 자투리 토큰 번호</b>를 읽는다.
 *
 * <h2>왜 필요한가 — 자투리가 체인에만 있고 DB 에는 번호가 없었다</h2>
 *
 * <p>컨트랙트의 {@code confirmProduction} 은 투입 소재를 소각하면서 <b>자투리를 새 토큰으로
 * 발행한다</b>({@code mintDerivedMaterial}). 그런데 화면은 그 트랜잭션 해시만 서버에
 * 보고했고 — {@code useProductionChainSteps} 의 "3) confirmProduction … 트랜잭션만 보고" —
 * 서버는 발행된 토큰 번호를 알 길이 없었다.
 *
 * <p>그래서 DB 의 자투리 소재는 {@code token_id} 가 비어 있었다. 겉으로는 아무 문제가 없다.
 * 보유 목록에도 뜨고 다음 생산에 투입할 수도 있다. <b>가공을 맡길 때만 막힌다</b> —
 * {@code ProcessingChainService.prepare} 가 토큰 없는 소재를 거절하기 때문이다.
 *
 * <p>실제로 그렇게 새어 나갔다. 자투리 500g 으로 가공을 요청하고 <b>가공비 2,500원을 결제하고
 * 업체가 수락까지 한 뒤</b>, 소재를 인계하려는 순간 "현재 상태에서는 가공 요청을 변경할 수
 * 없습니다" 로 막혔다. 원인을 말해 주지 않는 문구라 담당자는 무엇이 잘못됐는지 알 수 없었고,
 * 되돌릴 방법도 없었다.
 *
 * <h2>영수증을 읽는 이유</h2>
 *
 * <p>화면이 토큰 번호를 보내게 할 수도 있다. 그러지 않는 이유는 소재 발행·거래 기록과 같다 —
 * <b>화면이 보낸 번호를 믿으면 아무 토큰이나 자기 소재로 만들 수 있다.</b> 체인이 실제로
 * 발행한 것만 붙인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionScrapReceiptReader {

    /** {@code ProductionScrapCreated(batchId, parentMaterialId, scrapMaterialId, amount, brand, occurredAt)} */
    private static final String SCRAP_CREATED_TOPIC = Hash.sha3String(
            "ProductionScrapCreated(uint256,uint256,uint256,uint256,address,uint256)").toLowerCase();

    private final BlockchainJsonRpcClient rpc;
    private final BlockchainProperties properties;

    /**
     * 부모 소재 토큰 → 발행된 자투리 토큰.
     *
     * <p>세 값이 모두 indexed 라 {@code topics} 에 들어 있다 — {@code topics[1]} 이 배치,
     * {@code topics[2]} 가 부모 소재, {@code topics[3]} 이 자투리다.
     *
     * <p><b>읽지 못하면 빈 map 을 돌려준다.</b> 자투리 연결은 생산 확정의 부수 작업이고, 그것
     * 때문에 소각 기록 자체를 실패시키면 체인은 소각됐는데 서버는 모르는 상태가 된다 — 지금
     * 고치려는 문제보다 나쁜 어긋남이다. 대신 경고를 남겨 나중에 메울 수 있게 한다.
     */
    public Map<Long, Long> scrapTokensOf(String txHash, long chainBatchId) {
        Map<Long, Long> byParent = new LinkedHashMap<>();
        String registry = properties.productionRegistryAddress();
        if (registry == null || registry.isBlank()) {
            return byParent;
        }
        try {
            JsonNode receipt = rpc.transactionReceipt(txHash);
            for (JsonNode log : receipt.path("logs")) {
                if (!same(registry, log.path("address").asText())) continue;
                JsonNode topics = log.path("topics");
                if (!topics.isArray() || topics.size() < 4) continue;
                if (!same(SCRAP_CREATED_TOPIC, topics.get(0).asText())) continue;
                if (unsignedLong(topics.get(1).asText()) != chainBatchId) continue;

                byParent.put(
                        unsignedLong(topics.get(2).asText()),   // parentMaterialId
                        unsignedLong(topics.get(3).asText()));  // scrapMaterialId
            }
        } catch (RuntimeException failure) {
            log.warn(
                    "Production scrap receipt read failed: txHash={} batch={} reason={} message={}",
                    txHash,
                    chainBatchId,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
            return Map.of();
        }
        return byParent;
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static long unsignedLong(String hex) {
        return new BigInteger(hex.startsWith("0x") ? hex.substring(2) : hex, 16).longValueExact();
    }
}
