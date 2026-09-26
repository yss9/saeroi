package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.dto.BlockchainTransactionResponse;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 대상에 기록된 온체인 트랜잭션 목록 조회.
 *
 * <p>대상이 존재하는지는 확인하지 않는다 — {@code EvidenceListService} 와 같은 이유로,
 * 기록이 없는 것과 대상이 없는 것을 구분하면 이 응답이 "그 id 가 존재하는가" 를 알려주는
 * 신호가 된다. 대상 존재 확인은 각 도메인의 상세 조회가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class BlockchainTransactionQueryService {

    private final BlockchainTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<BlockchainTransactionResponse> findAll(
            BlockchainReferenceType referenceType, Long referenceId) {
        return transactionRepository
                .findByReferenceTypeAndReferenceIdOrderByConfirmedAtDesc(referenceType, referenceId)
                .stream()
                .map(BlockchainTransactionResponse::from)
                .toList();
    }
}
