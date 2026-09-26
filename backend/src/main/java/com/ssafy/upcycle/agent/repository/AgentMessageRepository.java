package com.ssafy.upcycle.agent.repository;

import com.ssafy.upcycle.agent.entity.AgentMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    List<AgentMessage> findByConversationIdOrderBySequenceNoAsc(Long conversationId);

    /**
     * 다음 순번. 대화가 비었으면 {@code null} 이 오므로 호출부가 0 으로 시작한다.
     *
     * <p>순번을 DB 에서 계산하는 이유는 한 턴에 여러 메시지를 저장하는 사이 다른 요청이
     * 끼어들 수 있기 때문이다. {@code uk_agent_message_sequence} 가 최후의 방어선이다.
     */
    @Query("select max(m.sequenceNo) from AgentMessage m where m.conversationId = :conversationId")
    Integer findMaxSequenceNo(@Param("conversationId") Long conversationId);
}
