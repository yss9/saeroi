package com.ssafy.upcycle.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 배치에 소재 여러 건을 한 번에 연결하는 요청.
 *
 * <h2>왜 목록으로 받는가</h2>
 *
 * <p>한 건씩 받으면 <b>중간에 실패했을 때 앞의 것들이 잠긴 채 남는다.</b> 소재 셋을 넣다가
 * 세 번째에서 잔량이 모자라면 앞의 둘은 이미 잠겨 있고, 사용자는 화면에서 실패를 봤는데
 * 소재는 묶여 있는 상태가 된다. 프론트가 어디까지 성공했는지 추적해 되돌려야 하고, 그
 * 되돌리기가 실패하면 아무도 쓰지 않는 잠금이 남는다.
 *
 * <p>목록으로 받으면 <b>전부 성공하거나 전부 실패한다.</b> 생산 화면은 소재 여러 개를 골라
 * 한 번에 저장하는 형태라 이쪽이 실제 사용에도 맞는다.
 *
 * <p>단건도 원소 하나짜리 목록으로 보내면 되므로 할 수 있는 일이 줄지 않는다.
 */
public record ProductionInputsRequest(

        @NotEmpty(message = "투입할 소재를 하나 이상 지정해야 합니다.")
        @Size(max = MAX_INPUTS, message = "한 번에 최대 " + MAX_INPUTS + "건까지 투입할 수 있습니다.")
        @Valid
        List<ProductionInputRequest> inputs) {

    /**
     * 한 요청의 투입 상한.
     *
     * <p>상한이 없으면 한 번의 요청으로 소재 수천 건을 잠글 수 있다. 배치 하나에 들어가는
     * 소재는 보통 몇 개이고, 더 필요하면 나눠 부르면 된다 — 증빙 업로드가 같은 이유로 같은
     * 상한을 둔다.
     */
    public static final int MAX_INPUTS = 20;
}
