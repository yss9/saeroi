package com.ssafy.upcycle.material.service;

/**
 * 소재 최초 발행 — 등록된 소재의 <b>보유 관계를 처음 만드는</b> 일.
 *
 * <p>이 이음매가 필요한 이유는 발행의 정본이 앞으로 바뀌기 때문이다. 지금은 DB 가 정본이지만
 * ({@link OffChainMaterialIssuer}), 컨트랙트가 배포되면 {@code MaterialToken.mintMaterial}
 * 이 정본이 되고 DB 는 그 결과를 받아 적는 읽기 모델이 된다. 그때 구현을 하나 더 만들어
 * 갈아 끼우면 되고, {@link MaterialRegistrationService} 는 다시 열지 않는다.
 *
 * <p>{@code PaymentGateway} 와 같은 모양이다. 거기서도 Mock 과 실연동이 같은 계약을 지키게
 * 해서, <b>Mock 에서만 통과하는 경로</b>가 생기지 않게 했다. 여기도 같은 이유다 — 발행 방식이
 * 무엇이든 "등록자가 전량을 보유한다" 는 사실과 그 검증은 동일해야 한다.
 *
 * <p><b>구현은 등록 트랜잭션 안에서 호출된다.</b> 발행이 실패하면 소재 행도 함께 롤백되어야
 * 하기 때문이다. 보유 관계 없는 소재는 조회에는 뜨는데 팔 수도 가공에 넣을 수도 없어서,
 * 사용자에게는 설명할 수 없는 상태로 보인다.
 */
public interface MaterialIssuer {

    /**
     * 등록자에게 전량을 발행한다.
     *
     * @param materialId 저장이 끝나 id 가 확정된 소재
     * @param ownerId 등록자. 발행 시점의 소유자다
     * @param quantity 그램 단위 전량
     */
    void issue(Long materialId, Long ownerId, long quantity);
}
