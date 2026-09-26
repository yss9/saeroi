package com.ssafy.upcycle.agent.repository;

import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.ConversationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {

    /**
     * 소유자까지 함께 조건에 넣는다.
     *
     * <p>{@code findById} 로 읽고 서비스에서 소유자를 비교하면, 비교를 빠뜨린 경로가 하나라도
     * 생기는 순간 남의 대화가 새어 나간다. 조회 자체에 소유자를 박아 두면 그 실수를 할 수 없다.
     */
    Optional<AgentConversation> findByIdAndBrandUserId(Long id, Long brandUserId);

    List<AgentConversation> findByBrandUserIdOrderByUpdatedAtDesc(Long brandUserId, Pageable pageable);

    /**
     * 진행 중인 대화. 담당자당 최대 하나다.
     *
     * <p>여러 개를 허용하면 "어느 대화에서 진행 중인 건인가" 를 매번 따져야 하고, 같은 소재를
     * 두 대화가 동시에 묶으려는 상황이 생긴다. 하나로 묶으면 그 문제가 통째로 사라진다.
     */
    Optional<AgentConversation> findByBrandUserIdAndStatus(Long brandUserId, ConversationStatus status);
}
