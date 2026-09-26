package com.ssafy.upcycle.material.entity;

/** {@code material_event.reference_id}가 가리키는 업무 레코드 종류. */
public enum MaterialEventReferenceType {
    MATERIAL,
    MATERIAL_LOCK,
    MATERIAL_LISTING,
    MATERIAL_TRADE,
    PROCESSING_REQUEST,
    PROCESSING_QUOTE,
    PROCESSING_JOB,
    PRODUCTION_BATCH,
    PRODUCTION_INPUT,
    EVIDENCE_FILE,
    BLOCKCHAIN_TRANSACTION
}
