package com.ssafy.upcycle.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.blockchain.service.BlockchainJsonRpcClient;
import com.ssafy.upcycle.common.exception.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 테스트에서 체인이 무엇을 답할지 정해 준다.
 *
 * <p>{@code ChainTransactionRecorder} 가 해시를 체인에서 확인한 뒤에만 기록하게 되면서,
 * 온체인 연동 테스트는 영수증을 말해 줘야 한다. 아무것도 말하지 않으면 <b>해시가 체인에
 * 없는 것과 같게</b> 동작한다 — 실제 노드가 그 경우에 내는 오류와 같다.
 *
 * <h2>왜 {@code @MockitoBean} 이 아닌가</h2>
 *
 * <p>{@code @MockitoBean} 은 스프링 <b>컨텍스트 캐시 키를 바꾼다.</b> 8개 테스트 클래스에
 * 각각 붙이면 지금 공유하던 컨텍스트가 쪼개지고, 캐시가 넘치면 오래된 컨텍스트가 닫히면서
 * {@code create-drop} 이 공유 H2 의 스키마를 지운다 — 관계없는 테스트가 "Table not found"
 * 로 깨진다({@code ChainLinkApiTests} 의 프로퍼티 주석이 경고하는 그 함정이다).
 *
 * <p>반면 <b>{@code @Autowired} 는 캐시 키를 바꾸지 않는다.</b> 그래서 목을 주입하는 대신
 * 진짜 빈을 하나 두고 테스트가 그 내용을 정하게 한다.
 *
 * <p>컨텍스트당 싱글턴이라 상태가 테스트 사이에 남는다. 각 테스트가 {@code @BeforeEach}
 * 에서 자기가 기대하는 값을 정하는 것을 전제로 한다.
 */
@Primary
@Component
public class ScriptedReceiptClient implements BlockchainJsonRpcClient {

    private JsonNode scripted;
    private final java.util.Map<String, JsonNode> byHash = new java.util.HashMap<>();

    /**
     * 해시별로 다르게 답한다.
     *
     * <p>한 흐름 안에서 <b>서명자가 바뀌는</b> 경우에 쓴다 — 가공은 브랜드가 작업을 만들고
     * 가공사가 수령을 확인하므로, 단계마다 영수증의 {@code from} 이 달라야 한다.
     */
    public void expectFor(String txHash, String to, String from) {
        byHash.put(txHash.toLowerCase(), FakeReceipt.success(to, from));
    }

    /** 이 컨트랙트로 이 지갑이 보낸 성공한 트랜잭션이 있다고 답한다. */
    public void expect(String to, String from) {
        scripted = FakeReceipt.success(to, from);
    }

    /** 블록번호까지 정한다 — 기록이 요청이 아니라 영수증을 따르는지 확인할 때 쓴다. */
    public void expect(String to, String from, long blockNumber) {
        scripted = FakeReceipt.success(to, from, blockNumber);
    }

    /** 해시는 있지만 리버트된 트랜잭션. */
    public void expectReverted(String to, String from) {
        scripted = FakeReceipt.reverted(to, from);
    }

    /** 체인에 그런 해시가 없다 — 기본 상태. */
    public void expectMissing() {
        scripted = null;
        byHash.clear();
    }

    @Override
    public JsonNode transactionReceipt(String txHash) {
        JsonNode answer = byHash.getOrDefault(
                txHash == null ? "" : txHash.toLowerCase(), scripted);
        if (answer == null) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
        }
        return answer;
    }
}
