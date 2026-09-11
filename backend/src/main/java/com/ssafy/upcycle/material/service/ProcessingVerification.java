package com.ssafy.upcycle.material.service;

/** 소재가 브랜드 승인까지 완료된 가공 산출물인지 묻는 도메인 경계. */
public interface ProcessingVerification {
    boolean hasConfirmedProcessingResult(Long materialId);
}
