package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialJourneyStage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소재 상세 화면의 Journey Timeline (S15P21E104-73).
 *
 * <p>Product Passport 의 {@code ProductJourneyResponse} 와 이름은 비슷하지만 성격이 다르다 —
 * 저쪽은 공개 화면이라 노드·간선 그래프로 여러 업체를 지도에 그리고, 이쪽은 <b>인증된
 * 소재 상세 화면</b>이라 한 소재에 일어난 사건을 시간순 한 줄로 보여주면 충분하다. 그래프를
 * 만들 필요가 없어 {@link com.ssafy.upcycle.material.service.MaterialEventQueryService} 가
 * 이미 갖고 있는 사건 로그에 단계 분류·행위자 이름·트랜잭션 해시만 얹는다.
 */
public record MaterialJourneyResponse(Long materialId, List<Entry> events) {

    public record Entry(
            MaterialEventType eventType,
            MaterialJourneyStage stage,
            String actorWallet,
            String actorName,
            Long quantity,
            LocalDateTime timestamp,
            String txHash,
            String detail) {}
}
