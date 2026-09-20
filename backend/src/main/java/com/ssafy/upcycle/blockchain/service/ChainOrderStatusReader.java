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
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

/**
 * 마켓플레이스 주문의 <b>체인 상태</b>를 읽는다.
 *
 * <h2>왜 서버가 체인을 읽어야 하는가</h2>
 *
 * <p>서버 DB 만으로는 "수령 서명이 남았는가" 를 알 수 없다. {@code buyer_signed_at} 이
 * 그 답이어야 하지만 수령 확정 시 서명 없이 채워진다. 그래서 대화가 "서명할 것이 없다" 고
 * 잘못 답했다 — 실제로는 체인 주문이 {@code SHIPPED} 에서 멈춰 소재가 판매자 잠금에 남아
 * 있었다.
 *
 * <p>{@code needsChainOrder}(주문 기록 여부)는 DB 로 판정되지만 수령은 그렇지 않다.
 * 체인이 정본이므로 체인에 물어본다.
 *
 * <h2>읽기 전용이고, 실패해도 흐름을 막지 않는다</h2>
 *
 * <p>{@code eth_call} 이라 가스도 서명도 필요 없다. RPC 가 죽어 있거나 설정이 비어 있으면
 * {@code Optional.empty()} 를 돌려준다 — 체인을 못 읽는 것이 거래 조회를 막을 이유는 없고,
 * 모르면 "모른다" 로 두는 것이 틀린 값을 내는 것보다 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainOrderStatusReader {

    /**
     * 컨트랙트의 {@code OrderStatus}. <b>0번이 {@code NONE}</b> 이다.
     *
     * <p>이 한 칸 차이를 놓쳐 {@code SHIPPED} 를 {@code RECEIVED} 로 읽은 일이 있었다.
     * 프론트의 {@code ORDER_STATUS_ON_CHAIN} 과 같은 순서를 지킨다.
     */
    private static final int STATUS_SHIPPED = 2;

    private static final int STATUS_RECEIVED = 3;

    private final BlockchainProperties properties;

    /** 이 주문에 <b>구매자 수령 서명이 남았는가.</b> 모르면 비어 있다. */
    public Optional<Boolean> needsReceiptSignature(long chainTradeId) {
        return status(chainTradeId).map(status -> status == STATUS_SHIPPED);
    }

    /** 주문 상태 코드. 주문이 없으면 0({@code NONE})이다. */
    public Optional<Integer> status(long chainTradeId) {
        String marketplace = properties.marketplaceAddress();
        String rpcUrl = properties.rpcUrl();
        if (marketplace == null || marketplace.isBlank() || rpcUrl == null || rpcUrl.isBlank()) {
            return Optional.empty();
        }
        // 빈으로 두지 않고 필요할 때 만든다 — 기존 OnChainRole 게이트웨이와 같은 방식이다.
        // 설정이 비어도 기동을 막지 않아야 하고, 연결을 오래 쥐고 있을 이유도 없다.
        try (Web3j web3j = Web3j.build(new HttpService(rpcUrl))) {
            Function function = new Function(
                    "getOrder",
                    List.of(new Uint256(BigInteger.valueOf(chainTradeId))),
                    List.of(new TypeReference<OrderTuple>() {}));
            EthCall call = web3j
                    .ethCall(
                            Transaction.createEthCallTransaction(
                                    marketplace, marketplace, FunctionEncoder.encode(function)),
                            DefaultBlockParameterName.LATEST)
                    .send();
            if (call.hasError()) {
                log.debug("Chain order read failed: id={} error={}", chainTradeId, call.getError().getMessage());
                return Optional.empty();
            }
            var decoded = FunctionReturnDecoder.decode(call.getValue(), function.getOutputParameters());
            if (decoded.isEmpty()) {
                return Optional.empty();
            }
            DynamicStruct order = (DynamicStruct) decoded.get(0);
            // 마지막 필드가 status 다 (id, listingId, seller, buyer, materialId, quantity, status).
            var last = order.getValue().get(order.getValue().size() - 1);
            return Optional.of(((BigInteger) last.getValue()).intValue());
        } catch (Exception failure) {
            // 체인을 못 읽는 것이 거래 조회를 막을 이유는 없다.
            log.debug(
                    "Chain order read failed: id={} reason={}",
                    chainTradeId,
                    failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 이미 수령까지 기록됐는가. 서명 카드를 띄우지 않아도 되는 상태다. */
    public boolean alreadyReceived(long chainTradeId) {
        return status(chainTradeId).filter(status -> status >= STATUS_RECEIVED).isPresent();
    }

    /** {@code getOrder} 가 돌려주는 튜플. 필드 순서는 컨트랙트와 같아야 한다. */
    public static class OrderTuple extends DynamicStruct {
        public OrderTuple() {
            super(
                    new Uint256(BigInteger.ZERO),
                    new Uint256(BigInteger.ZERO),
                    new Address(Address.DEFAULT.toString()),
                    new Address(Address.DEFAULT.toString()),
                    new Uint256(BigInteger.ZERO),
                    new Uint256(BigInteger.ZERO),
                    new Uint8(BigInteger.ZERO));
        }
    }
}
