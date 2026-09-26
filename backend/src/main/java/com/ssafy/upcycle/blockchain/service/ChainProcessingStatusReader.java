package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

/**
 * 가공 작업의 <b>체인 상태</b>를 읽는다. {@link ChainOrderStatusReader} 와 같은 역할이다.
 *
 * <h2>왜 필요한가 — 두 방향으로 어긋난다</h2>
 *
 * <p>가공 발송은 두 가지 일이 짝을 이룬다. 컨트랙트의 {@code markShipped} 와 서버의
 * {@code ship}. 서버는 {@code chainJobId} 가 있는지만 봤는데, 그것은 "작업이 체인에
 * 등록됐다" 는 뜻이지 "발송이 체인에 남았다" 는 뜻이 아니다. 훨씬 앞 단계의 흔적이다.
 *
 * <p><b>DB 가 앞서는 경우.</b> 발송 API 를 화면 없이 직접 부르면 서버가 그냥 통과시킨다.
 * 그러면 DB 는 {@code SHIPPED}, 체인은 {@code ACCEPTED} 다. 며칠 뒤 가공업체가 수령을
 * 누를 때 {@code confirmReceipt} 가 {@code require(status == SHIPPED)} 에서 revert 한다 —
 * "Processing: invalid status". 그 함수가 체인에서 소재 보관을 브랜드 잠금에서 업체 잠금으로
 * 옮기는 자리라, 막히면 업체는 물건을 들고 있는데 아무 단계도 진행할 수 없다.
 * <b>막히는 사람이 담당자가 아니라 상대편이어서</b> 담당자는 왜 멈췄는지 알 수 없다.
 *
 * <p><b>체인이 앞서는 경우 — 이쪽이 더 흔하다.</b> 화면은 서명을 먼저 하고 서버를 뒤에
 * 부른다({@code signCustodyTransfer}). 서명이 체인에 들어간 뒤 서버 호출이 실패하면
 * (네트워크 끊김, 토큰 만료, 서버 재시작) 체인은 {@code SHIPPED} 인데 DB 는
 * {@code ACCEPTED} 로 남는다. 다시 시도하면 {@code markShipped} 가 revert 하고 — 체인이
 * 이미 {@code SHIPPED} 라 {@code require(ACCEPTED)} 를 어긴다 — 그 뒤의 서버 호출까지
 * 못 간다. <b>DB 가 영원히 따라잡지 못한다.</b>
 *
 * <p>가드가 두 경우를 함께 다룬다. DB 가 앞서려 하면 막고, 체인이 앞섰으면 DB 가 따라잡게
 * 통과시킨다.
 *
 * <h2>읽기 전용이고, 실패해도 흐름을 막지 않는다</h2>
 *
 * <p>{@code eth_call} 이라 가스도 서명도 필요 없다. 설정이 비어 있거나 RPC 가 죽어 있으면
 * {@code Optional.empty()} 를 돌려준다 — 모르면 "모른다" 로 두는 것이 틀린 값을 내는 것보다
 * 안전하다. 그 둘을 구분하는 것이 {@link #canRead()} 다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainProcessingStatusReader {

    /**
     * 컨트랙트의 {@code Status}. <b>0번이 {@code NONE}</b> 이다.
     *
     * <pre>
     *   0 NONE          1 REQUESTED   2 ACCEPTED    3 SHIPPED
     *   4 RECEIVED      5 PROCESSING  6 RESULT_SUBMITTED
     *   7 CONFIRMED     8 RESULT_REJECTED
     *   9 DECLINED     10 CANCELED   11 RETURN_SHIPPED  12 REJECTED_INPUT_RETURNED
     * </pre>
     */
    private static final int STATUS_SHIPPED = 3;

    /**
     * 발송이 <b>이미 체인에 남은</b> 상태들.
     *
     * <p><b>{@code >= SHIPPED} 로 비교하지 않는다.</b> 번호가 진행 순서가 아니다 —
     * {@code DECLINED}(9) 와 {@code CANCELED}(10) 가 {@code SHIPPED}(3) 보다 큰데, 그 둘은
     * 업체가 거절하거나 취소된 경우로 <b>발송이 일어난 적 없다.</b> 크기로 비교하면 거절된
     * 작업에 "발송이 남았다" 고 답해 DB 를 밀어 준다.
     */
    private static final Set<Integer> SHIPPED_OR_BEYOND = Set.of(
            STATUS_SHIPPED, 4, 5, 6, 7, 8, 11, 12);

    private final BlockchainProperties properties;

    /**
     * 서버가 가공 작업을 <b>읽을 수 있는 설정인가.</b>
     *
     * <p>"읽을 수 없다" 와 "읽었는데 아직 아니다" 는 다른 일인데 {@link #status} 는 둘 다 빈
     * 값으로 돌려준다. 선행 조건을 걸 때 그 둘을 같게 취급하면, 체인을 쓰지 않는 환경
     * (로컬·테스트)에서 발송이 통째로 막힌다.
     */
    public boolean canRead() {
        return properties.hasProcessingRegistry()
                && properties.rpcUrl() != null
                && !properties.rpcUrl().isBlank();
    }

    /** 작업 상태 코드. 작업이 없으면 0({@code NONE})이다. */
    public Optional<Integer> status(long chainJobId) {
        if (!canRead()) {
            return Optional.empty();
        }
        String registry = properties.processingRegistryAddress();
        try (Web3j web3j = Web3j.build(new HttpService(properties.rpcUrl()))) {
            Function function = new Function(
                    "getJob",
                    List.of(new Uint256(BigInteger.valueOf(chainJobId))),
                    // **튜플을 구조체로 받지 않는다.** 정적 필드만 든 튜플은 ABI 인코딩에서
                    // 그 필드들이 나열된 것과 같고, web3j 의 StaticStruct 디코딩은
                    // "Zero length fixed array is invalid type" 으로 터진다 — 주문 조회에서
                    // 실측으로 확인한 함정이다. 필드를 순서대로 선언하면 같은 바이트가 풀린다.
                    List.of(
                            new TypeReference<Address>() {},   // brand
                            new TypeReference<Address>() {},   // processor
                            new TypeReference<Bytes32>() {},   // shippingEvidenceHash
                            new TypeReference<Bytes32>() {},   // resultEvidenceHash
                            new TypeReference<Bytes32>() {},   // returnShippingEvidenceHash
                            new TypeReference<Uint8>() {},     // status
                            new TypeReference<Uint64>() {},    // createdAt
                            new TypeReference<Uint64>() {}));  // updatedAt
            EthCall call = web3j
                    .ethCall(
                            Transaction.createEthCallTransaction(
                                    registry, registry, FunctionEncoder.encode(function)),
                            DefaultBlockParameterName.LATEST)
                    .send();
            if (call.hasError()) {
                log.warn(
                        "Chain processing read failed: id={} error={}",
                        chainJobId,
                        call.getError().getMessage());
                return Optional.empty();
            }
            var decoded = FunctionReturnDecoder.decode(call.getValue(), function.getOutputParameters());
            if (decoded.size() < 6) {
                return Optional.empty();
            }
            // 여섯 번째가 status 다 (brand, processor, 해시 3개, status, …).
            return Optional.of(((BigInteger) decoded.get(5).getValue()).intValue());
        } catch (Exception failure) {
            log.warn(
                    "Chain processing read failed: id={} reason={} message={}",
                    chainJobId,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
            return Optional.empty();
        }
    }

    /** 브랜드의 <b>발송 서명이 체인에 남았는가.</b> 뒤 단계로 지나갔어도 참이다. */
    public boolean alreadyShipped(long chainJobId) {
        return status(chainJobId).filter(SHIPPED_OR_BEYOND::contains).isPresent();
    }
}
