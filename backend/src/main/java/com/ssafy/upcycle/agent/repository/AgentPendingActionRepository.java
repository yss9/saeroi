package com.ssafy.upcycle.agent.repository;

import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentPendingActionRepository extends JpaRepository<AgentPendingAction, Long> {

    List<AgentPendingAction> findByConversationIdAndStatus(Long conversationId, PendingActionStatus status);

    List<AgentPendingAction> findByConversationIdOrderByIdAsc(Long conversationId);

    /** 대화까지 함께 조건에 넣어, 남의 대화의 액션을 id 만으로 건드릴 수 없게 한다. */
    Optional<AgentPendingAction> findByIdAndConversationId(Long id, Long conversationId);
}
