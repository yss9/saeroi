package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier.Action;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 가공 작업의 한 단계가 체인에 남긴 <b>거래를 찾는다.</b> 화면이 거래 해시를 잃었을 때 쓴다.
 *
 * <h2>왜 필요한가 — 서명은 됐는데 기록이 멈췄다</h2>
 *
 * <p>수령 확인과 결과 승인은 서버가 그 거래의 영수증을 읽어야 기록할 수 있다. 영수증에만 있는
 * 값이 있기 때문이다 — 수령은 업체 쪽 잠금 번호, 승인은 새로 발행된 소재 번호. 그런데 서명이 체인에
 * 들어간 뒤 서버 호출이 실패하면(네트워크 끊김, 로그인 만료) 화면은 해시를 잃는다. 다시 누르면
 * 체인이 이미 그 단계를 지나 서명을 되돌리고, 보낼 해시가 없으니 기록이 영원히 멈춘다.
 *
 * <p>그래서 서버가 체인에서 직접 찾는다. 이 컨트랙트가 이 작업 번호로 남긴 그 단계의 이벤트를
 * 조회해 거래 해시를 얻고, 그 뒤는 해시를 받았을 때와 <b>똑같이</b> 영수증을 검증한다
 * ({@link ProcessingReceiptVerifier}). 서명자·컨트랙트·작업 번호를 모두 다시 보므로 이 경로로
 * 검증을 건너뛰는 일은 없다.
 *
 * <p>찾은 거래가 없거나 <b>둘 이상이면 비워 둔다.</b> 한 작업의 한 단계는 한 번만 일어나므로 둘 이상은
 * 설명할 수 없는 상태이고, 그중 하나를 고르면 엉뚱한 소재가 발행될 수 있다.
 *
 * <h2>어디부터 찾는가 — 작업이 체인에 등록된 블록부터</h2>
 *
 * <p>처음에는 0번 블록부터 찾았다. 공용 SSAFY 체인은 10초마다 블록이 쌓여 1,100만 블록이 넘고,
 * 노드는 그 구간을 10초 안에 훑지 못해 매번 시간 초과가 났다. 업체가 수령 서명을 마친 두 작업이
 * 그 때문에 "발송됨" 에 멈췄다.
 *
 * <p>작업의 어느 단계든 그 작업이 체인에 등록된 뒤에 일어난다. 등록 거래는 서버가 기록해 두므로
 * ({@code createJob}) 그 블록부터 찾으면 된다. 구간은 {@value #CHUNK_BLOCKS} 블록씩 나눠 묻는다 —
 * 약 11일치이고 0.5초 안에 답한다. 등록 기록이 없는 작업은 최근 {@value #FALLBACK_LOOKBACK_BLOCKS}
 * 블록(약 116일)만 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingChainTxFinder {

    static final long CHUNK_BLOCKS = 100_000;
    static final long FALLBACK_LOOKBACK_BLOCKS = 1_000_000;
    private static final String CREATE_JOB = "createJob";

    private final BlockchainJsonRpcClient rpc;
    private final BlockchainProperties properties;
    private final BlockchainTransactionRepository transactions;

    /**
     * @param jobId 서버의 가공 작업 번호. 체인에 등록된 블록을 찾는 데 쓴다
     * @param chainJobId 체인의 작업 번호. 이벤트의 두 번째 토픽이다
     */
    public Optional<String> find(Long jobId, long chainJobId, Action action) {
        String registry = properties.processingRegistryAddress();
        if (registry == null || registry.isBlank()) {
            return Optional.empty();
        }
        List<String> topics = List.of(action.topic(), jobTopic(chainJobId));
        long latest = rpc.latestBlockNumber();
        long from = startBlock(jobId, latest);

        Set<String> hashes = new LinkedHashSet<>();
        for (long chunkStart = from; chunkStart <= latest; chunkStart += CHUNK_BLOCKS) {
            long chunkEnd = Math.min(latest, chunkStart + CHUNK_BLOCKS - 1);
            for (JsonNode log : rpc.logs(registry, topics, chunkStart, chunkEnd)) {
                // 체인이 재정렬되며 빠진 로그는 없는 것으로 본다.
                if (log.path("removed").asBoolean(false)) {
                    continue;
                }
                String hash = log.path("transactionHash").asText("");
                if (!hash.isBlank()) {
                    hashes.add(hash.toLowerCase());
                }
            }
        }
        if (hashes.size() > 1) {
            log.warn("Processing step found in more than one transaction: chainJobId={} action={} txs={}",
                    chainJobId, action, hashes);
            return Optional.empty();
        }
        return hashes.stream().findFirst();
    }

    /** 작업이 체인에 등록된 블록. 기록이 없거나 이상하면 최근 구간으로 좁힌다. */
    private long startBlock(Long jobId, long latest) {
        long fallback = Math.max(0, latest - FALLBACK_LOOKBACK_BLOCKS + 1);
        if (jobId == null) {
            return fallback;
        }
        return transactions
                .findFirstByReferenceTypeAndReferenceIdAndFunctionNameAndStatusOrderByConfirmedAtDesc(
                        BlockchainReferenceType.PROCESSING, jobId, CREATE_JOB, BlockchainTransactionStatus.SUCCESS)
                .map(BlockchainTransaction::getBlockNumber)
                .filter(block -> block >= 0 && block <= latest)
                .orElse(fallback);
    }

    /** 작업 번호는 이벤트의 두 번째 토픽이다 — 32바이트로 채운 16진수. */
    private static String jobTopic(long chainJobId) {
        return "0x" + String.format("%064x", chainJobId);
    }
}
