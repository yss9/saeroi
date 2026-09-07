package com.ssafy.upcycle.evidence.entity;

/**
 * {@code evidence_file.reference_type} — 증빙이 무엇에 딸려 있는가.
 *
 * <p><b>이름이 {@code FileReference#type} 문자열과 같아야 한다.</b> 저장 경로의 첫 조각이
 * 이 값이고, 갈라지면 참조로 파일을 되찾을 수 없다.
 *
 * <p>{@code reference_id} 에는 FK 가 없다 — 대상 테이블이 넷이라 걸 수 없다. 그래서
 * <b>대상이 실제로 존재하는지는 애플리케이션이 확인해야 한다.</b> 확인하지 않으면 없는
 * 소재를 가리키는 증빙 행이 남고, 그 해시는 온체인에 올라갈 수 있다.
 */
public enum EvidenceReferenceType {
    MATERIAL,
    PROCESSING_JOB,
    PRODUCTION_BATCH,
    PRODUCTION_INPUT
}
