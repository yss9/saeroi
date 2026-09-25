package com.ssafy.upcycle.agent.service.step;

import com.ssafy.upcycle.blockchain.service.ChainProductionStatusReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 생산의 온체인 기록이 <b>끝났는가</b>를 한 곳에서 판정한다.
 *
 * <h2>왜 따로 뺐나 — 막대와 대화가 다른 말을 했다</h2>
 *
 * <p>진행 막대({@code AgentJourneyProgress})와 남은 일 목록({@link BrandStepResolver})이
 * 각자 판정했다. 막대는 {@code chainBatchId} 가 채워졌으면 "블록체인 기록" 칸을 완료로
 * 칠했고, 목록은 체인을 읽어 아직 남았다고 말했다. 배치 6에서 <b>같은 화면이 두 가지를 동시에
 * 보여줬다</b> — 위쪽 막대는 끝났다고, 아래 대화는 서명이 남았다고. 담당자는 둘 중 어느 것이
 * 맞는지 알 방법이 없다.
 *
 * <p>판정을 한 곳에 두면 그 어긋남이 생길 자리가 없다.
 *
 * <h2>판정 규칙</h2>
 *
 * <p>{@code chainBatchId} 는 네 번의 서명 중 <b>첫 번째</b>({@code createBatch})의 산물이다.
 * 그 값만으로는 1단계에서 멈춘 배치를 완료로 읽는다. 그래서 체인에 직접 묻는다.
 *
 * <p><b>못 읽으면 끝난 것으로 본다.</b> 체인을 쓰지 않는 환경에서 끝낼 방법이 없는 일을
 * 남기면, 담당자는 지울 수 없는 숙제를 보게 되고 모델은 매 턴 그것을 꺼낸다.
 *
 * <p><b>4단계(개체 연결)는 제품 등록을 기다리지 않는다.</b> 예전에는 제품이 연결된 뒤에만 남은 일로 보았다 —
 * 개체 수가 생산 수량과 같아야 한다는 조건을 제품 등록과 묶어 읽었다. 그런데 개체는 생산 확정이 만들고
 * ({@code ProductionConfirmService.produceItems}), 4단계는 그 개체 번호만 체인에 올린다. 서버도 제품을 요구하지
 * 않는다({@code ProductionChainLinkService.linkProductItems}). 그 판정 때문에 3단계 뒤에 체인 기록이 "끝난" 것으로
 * 보였고, 모델은 판매가도 묻지 않고 제품부터 등록해 0원짜리 제품이 판매까지 갔다. 이제 네 단계를 모두 마친
 * 뒤에 제품 정보를 정한다.
 */
@Component
@RequiredArgsConstructor
public class ProductionChainProgress {

    private final ChainProductionStatusReader chainBatches;

    /**
     * 온체인 기록이 아직 남았는가. 네 단계 중 하나라도 남았으면 참이다.
     *
     * @param chainBatchId 서버가 아는 온체인 배치 번호. 없으면 시작조차 안 한 것이다
     */
    public boolean isPending(Long chainBatchId) {
        if (chainBatchId == null) {
            return true;
        }
        if (!chainBatches.isConfirmedOnChain(chainBatchId).orElse(true)) {
            return true;
        }
        return chainBatches.linkedItemCount(chainBatchId).orElse(1) == 0;
    }

    /** {@link #isPending} 의 반대. 막대에 칠할 때 읽기 편하라고 둔다. */
    public boolean isRecorded(Long chainBatchId) {
        return !isPending(chainBatchId);
    }
}
