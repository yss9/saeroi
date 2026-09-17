package com.ssafy.upcycle.user.entity;

/** 사업자 계정의 온체인 역할 심사 상태. 지갑 미등록 여부는 응답에서 별도로 계산한다. */
public enum CompanyRoleApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
