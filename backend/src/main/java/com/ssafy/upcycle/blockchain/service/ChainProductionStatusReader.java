package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

/**
 * 생산 배치의 <b>체인 상태</b>를 읽는다.
 *
 * <h2>왜 필요한가 — {@code chainBatchId} 는 1단계의 흔적일 뿐이다</h2>
 *
 * <p>생산 온체인 기록은 서명이 네 번이다. 서버가 가진 {@code chain_batch_id} 는 그중
 * <b>첫 번째</b>({@code createBatch})가 성공했다는 뜻밖에 없다. 그런데 남은 일을 판정할 때
 * 그 값이 채워졌는지만 보면, 1단계에서 멈춘 배치를 "기록이 끝났다" 로 읽는다.
 *
 * <p>실제로 그렇게 묻혔다. 배치 6은 {@code chain_batch_id=12} 를 들고 있지만 체인에서는
 * {@code DRAFT} — 담당자가 첫 서명 뒤 지갑을 닫았다. 남은 일 목록은 그 배치에 대해
 * 아무 말도 하지 않았고, 배치는 확정된 채로 영원히 멈춰 있었다. 그 소재는 이미 태워진
 * 것으로 <b>DB 에서만</b> 줄어 있어서, 체인만 보면 브랜드가 여전히 갖고 있는 상태다.
 *
 * <h2>4단계는 따로 본다</h2>
 *
 * <p>{@code linkProductItems} 는 제품 개체가 있어야 부를 수 있다. 개수가 선언한 생산 수량과
 * 정확히 같아야 해서, 제품을 등록하기 전에는 부를 수 없다. 그래서 상태와 연결 개수를 따로
 * 돌려주고, 무엇을 권할지는 부르는 쪽이 정한다.
 *
 * <h2>읽기 전용이고, 실패해도 흐름을 막지 않는다</h2>
 *
 * <p>{@code eth_call} 이라 가스도 서명도 필요 없다. 설정이 비어 있거나 RPC 가 죽어 있으면
 * {@code Optional.empty()} 를 돌려준다 — 모르면 "모른다" 로 두는 것이 틀린 값을 내는 것보다
 * 안전하다. 그 둘을 가르는 것이 {@link #canRead()} 다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainProductionStatusReader {

    /**
     * 컨트랙트의 {@code Status}. <b>0번이 {@code NONE}</b> 이다.
     *
     * <pre>
     *   0 NONE   1 DRAFT(createBatch)   2 SIGNING(submitPlanAndLock)
     *   3 CONFIRMED(confirmProduction)  4 FAILED   5 CANCELED
     * </pre>
     */
    public static final int STATUS_CONFIRMED = 3;

    private final BlockchainProperties properties;

    /** 생산 배치를 <b>읽을 수 있는 설정인가.</b> "못 읽는다" 와 "아직 아니다" 는 다른 일이다. */
    public boolean canRead() {
        return properties.hasProductionRegistry() && properties.hasRpcUrl();
    }

    /** 배치 상태 코드. 배치가 없으면 0({@code NONE})이다. */
    public Optional<Integer> status(long chainBatchId) {
        if (!canRead()) {
            return Optional.empty();
        }
        Function function = new Function(
                "getBatch",
                List.of(new Uint256(BigInteger.valueOf(chainBatchId))),
                // **튜플을 구조체로 받지 않는다.** 정적 필드만 든 튜플은 ABI 인코딩에서 그
                // 필드들이 나열된 것과 같고, web3j 의 StaticStruct 디코딩은 "Zero length
                // fixed array is invalid type" 으로 터진다 — 주문 조회에서 실측으로 확인한
                // 함정이다. 필드를 순서대로 선언하면 같은 바이트가 풀린다.
                List.of(
                        new TypeReference<Address>() {},  // brand
                        new TypeReference<Uint256>() {},  // productQuantity
                        new TypeReference<Bytes32>() {},  // evidenceHash
                        new TypeReference<Uint8>() {},    // status
                        new TypeReference<Uint64>() {},   // createdAt
                        new TypeReference<Uint64>() {},   // confirmedAt
                        new TypeReference<Uint16>() {})); // upcycledRatioBps

        return call(function).map(values -> ((BigInteger) values.get(3).getValue()).intValue());
    }

    /**
     * 배치에 연결된 제품 개체 수. 0 이면 4단계({@code linkProductItems})가 아직이다.
     */
    public Optional<Integer> linkedItemCount(long chainBatchId) {
        if (!canRead()) {
            return Optional.empty();
        }
        Function function = new Function(
                "getProductItems",
                List.of(new Uint256(BigInteger.valueOf(chainBatchId))),
                List.of(new TypeReference<DynamicArray<Uint256>>() {}));

        return call(function).map(values -> ((List<?>) values.get(0).getValue()).size());
    }

    /**
     * 온체인 기록의 <b>1~3단계가 끝났는가.</b> 읽지 못하면 비어 있다.
     *
     * <p>4단계는 제품 개체가 있어야 부를 수 있어 여기 넣지 않는다 —
     * {@link #linkedItemCount} 로 따로 본다.
     */
    public Optional<Boolean> isConfirmedOnChain(long chainBatchId) {
        return status(chainBatchId).map(status -> status == STATUS_CONFIRMED);
    }

    private Optional<List<org.web3j.abi.datatypes.Type>> call(Function function) {
        String registry = properties.productionRegistryAddress();
        try (Web3j web3j = Web3j.build(new HttpService(properties.rpcUrl()))) {
            EthCall response = web3j
                    .ethCall(
                            Transaction.createEthCallTransaction(
                                    null, registry, FunctionEncoder.encode(function)),
                            DefaultBlockParameterName.LATEST)
                    .send();
            if (response.hasError() || response.getValue() == null) {
                log.debug(
                        "Production chain read returned no value: function={} error={}",
                        function.getName(),
                        response.hasError() ? response.getError().getMessage() : "null");
                return Optional.empty();
            }
            var decoded = FunctionReturnDecoder.decode(
                    response.getValue(), function.getOutputParameters());
            return decoded.isEmpty() ? Optional.empty() : Optional.of(decoded);
        } catch (Exception unreachable) {
            // 체인을 못 읽는 것이 서버가 멈출 이유는 아니다. 부르는 쪽이 "모른다" 로 다룬다.
            log.warn(
                    "Production chain read failed: function={} reason={}",
                    function.getName(),
                    unreachable.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
