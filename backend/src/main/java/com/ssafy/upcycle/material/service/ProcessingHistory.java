package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.processing.entity.ProcessType;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 가공 작업이 무엇을 했는지 묻는 도메인 경계. {@link ProcessingVerification} 과 같은 자리다.
 *
 * <p>Material 도메인은 Processing 도메인을 참조하지 않는다 — 소재는 가공보다 먼저 있고,
 * 가공 없이도 성립한다. 그래서 필요한 질문만 인터페이스로 뚫고 구현은 저쪽에 둔다.
 *
 * <p><b>{@link ProcessType} 하나는 넘어온다.</b> 화면이 소재에 "세척·해체" 배지를 다는 것이
 * 이 경계를 뚫는 이유이므로, 그 값을 Material 쪽 enum 으로 한 번 더 번역하면 두 enum 이
 * 언젠가 갈라지고 번역표가 조용히 낡는다. 값 타입 하나만 건너오게 하고 나머지
 * (작업 상태, 견적, 산출물)는 넘어오지 않는다.
 */
public interface ProcessingHistory {

    /**
     * 확정된 가공 작업들이 실제로 수행한 가공 종류.
     *
     * <p>확정되지 않았거나 없는 작업 id 는 <b>결과 맵에서 빠진다</b> — 빈 집합으로 채우지
     * 않는다. "아직 확정되지 않았다" 와 "확정됐는데 가공 종류가 하나도 없다" 는 다른 일이고,
     * 후자는 {@code processing_request_type} 이 비었다는 뜻이라 데이터가 어긋난 상태다.
     */
    Map<Long, Set<ProcessType>> findConfirmedProcessTypes(Collection<Long> processingJobIds);
}
