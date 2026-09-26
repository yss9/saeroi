package com.ssafy.upcycle.blockchain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.http.HttpService;

/**
 * 체인에 <b>이미 쓰인 제품 개체 번호</b>를 빠짐없이 센다.
 *
 * <p>컨트랙트는 우리 {@code product_item.id} 를 그대로 개체 번호로 쓴다. DB 를 비우면 그
 * id 가 1부터 다시 시작하는데 체인에는 옛 번호가 남아 있어, 다시 연결하려 하면
 * "Production: product already linked" 로 거절당한다.
 *
 * <p>개체 번호를 1부터 훑으면 어디서 멈출지 알 수 없다 — 0 이 나와도 그 뒤에 쓰인 것이 있을
 * 수 있다. 대신 <b>배치를 전부 열어</b> 각 배치가 들고 있는 개체를 모으면 빠짐이 없다.
 * 배치 수는 {@code nextBatchId} 가 알려 준다.
 */
class ProductionChainProbeTests {

    private static final String RPC = "https://rpc.ssafy-blockchain.com";
    private static final String REGISTRY = "0x65B4D067E929a7Ce64E8d5896bD8D4e0efE6DD3a";

    @Test
    void 체인에_쓰인_개체_번호를_전부_센다() throws Exception {
        try (Web3j web3j = Web3j.build(new HttpService(RPC))) {
            long nextBatchId = number(web3j, "nextBatchId", List.of());
            System.out.println("[probe] nextBatchId = " + nextBatchId);

            var used = new TreeSet<Long>();
            for (long batchId = 1; batchId < nextBatchId; batchId++) {
                for (long itemId : itemsOf(web3j, batchId)) {
                    used.add(itemId);
                }
            }
            System.out.println("[probe] 이미 쓰인 개체 번호 = " + used);
            System.out.println("[probe] 안전한 다음 번호 = " + (used.isEmpty() ? 1 : used.last() + 1));

            // 옮긴 번호가 정말 비어 있는지 본다. 0 이면 어느 배치에도 안 묶인 것이다.
            for (long itemId : new long[] {643L, 644L, 645L}) {
                Function owner = new Function(
                        "productItemBatch",
                        List.of(new Uint256(BigInteger.valueOf(itemId))),
                        List.of(new TypeReference<Uint256>() {}));
                List<Type> got = FunctionReturnDecoder.decode(
                        call(web3j, FunctionEncoder.encode(owner)), owner.getOutputParameters());
                System.out.println("[probe] productItemBatch(" + itemId + ") = "
                        + (got.isEmpty() ? "?" : got.get(0).getValue()));
            }
        }
    }

    private List<Long> itemsOf(Web3j web3j, long batchId) throws Exception {
        Function function = new Function(
                "getProductItems",
                List.of(new Uint256(BigInteger.valueOf(batchId))),
                List.of(new TypeReference<DynamicArray<Uint256>>() {}));
        List<Type> decoded = FunctionReturnDecoder.decode(
                call(web3j, FunctionEncoder.encode(function)), function.getOutputParameters());
        List<Long> ids = new ArrayList<>();
        if (decoded.isEmpty()) {
            return ids;
        }
        for (Object value : (List<?>) decoded.get(0).getValue()) {
            ids.add(((Uint256) value).getValue().longValue());
        }
        return ids;
    }

    private long number(Web3j web3j, String name, List<Type> inputs) throws Exception {
        Function function = new Function(name, inputs, List.of(new TypeReference<Uint256>() {}));
        List<Type> decoded = FunctionReturnDecoder.decode(
                call(web3j, FunctionEncoder.encode(function)), function.getOutputParameters());
        return decoded.isEmpty() ? 0 : ((BigInteger) decoded.get(0).getValue()).longValue();
    }

    private String call(Web3j web3j, String data) throws Exception {
        return web3j
                .ethCall(
                        Transaction.createEthCallTransaction(REGISTRY, REGISTRY, data),
                        DefaultBlockParameterName.LATEST)
                .send()
                .getValue();
    }
}
