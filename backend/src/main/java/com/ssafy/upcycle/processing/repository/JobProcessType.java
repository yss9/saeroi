package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.ProcessType;

/**
 * "이 가공 작업은 이 종류의 가공을 했다" 한 줄.
 *
 * <p>가공 종류는 {@code processing_request_type} 에 있고 작업은 {@code processing_job} 이라,
 * 작업 여러 건의 가공 종류를 한 번에 알려면 두 테이블을 조인한 뒤 <b>작업별로 다시 접어야</b>
 * 한다. 그 중간 모양이다.
 *
 * <p>{@code Object[]} 로 받지 않는 이유는 호출부에서 {@code row[0]} 이 무엇인지 알 수 없고,
 * 캐스팅이 틀려도 컴파일러가 잡아주지 않기 때문이다.
 */
public record JobProcessType(Long jobId, ProcessType processType) {
}
