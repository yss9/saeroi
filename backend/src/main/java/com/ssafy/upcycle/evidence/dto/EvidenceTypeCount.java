package com.ssafy.upcycle.evidence.dto;

import com.ssafy.upcycle.evidence.entity.EvidenceType;

/**
 * 증빙 종류 하나의 건수. S15P21E104-286 §6-1.
 *
 * <p>{@code Map<EvidenceType, Long>} 로 내려주지 않는다. 맵은 <b>키 순서가 계약이 아니고</b>
 * (JSON 객체 순서를 신뢰할 수 없다) 프론트가 라벨 순서를 스스로 정해야 한다. 배열이면
 * 서버가 정한 순서(=열거 순서)가 그대로 화면 순서가 된다 — 목록 정렬을 서버가 고정한
 * 이유와 같다 ({@code EvidenceFileRepository} 주석).
 *
 * <p>건수가 0인 종류는 <b>넣지 않는다.</b> "계근표 0건" 은 화면에 그릴 값이 아니고,
 * 넣으면 종류가 늘어날 때마다 빈 줄이 늘어난다.
 */
public record EvidenceTypeCount(EvidenceType evidenceType, long count) {}
