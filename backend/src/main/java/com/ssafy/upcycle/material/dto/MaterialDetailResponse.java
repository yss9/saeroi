package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialStatus;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.entity.MaterialVerificationState;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소재 상세. 티켓이 요구한 6블록 — 기본정보 · 수량 · Owner · Evidence · Verification · 상태.
 *
 * <h2>{@code verificationState} 가 판정하는 범위</h2>
 *
 * <p>저장 컬럼이 아니라 {@code MaterialVerificationStateResolver} 가 조회 시점에 계산한
 * 값이다. 다만 <b>지금 판정되는 단계는 {@code TRANSACTION_VERIFIED} 와
 * {@code REGISTERED} 둘뿐이다.</b>
 *
 * <p>나머지 세 단계는 판정 근거가 아직 없다 — {@code material.signed_at} 컬럼이 없고,
 * 승인 시각을 남길 {@code processing_job} 도, {@code production_input} 엔티티도 없다.
 * 그래서 <b>결과 자체는 오늘 기준으로 정확하다.</b> 도달할 수 없는 단계를 놓치는 것이지
 * 틀리게 보고하는 것이 아니다.
 *
 * <p>화면에서 검증 배지를 그린다면 이 범위를 넘는 표현은 쓰지 않는다.
 *
 * <h2>null 이 정상인 필드들</h2>
 *
 * <p>{@code tokenId}·{@code chainId}·{@code contractAddress} 는 mint 결과이고, 그 결과를
 * 기록하는 confirm 엔드포인트가 아직 없다. <b>null 이 "등록됐지만 온체인에 없음" 의 유일한
 * 신호</b>이므로 별도 boolean 을 만들지 않고 그대로 내려준다.
 *
 * <p>{@code evidenceHash}·{@code manifestUrl} 도 증빙 등록 경로가 없어 당분간 null 이다.
 * {@code evidence_file} 테이블의 파일 목록은 그 엔티티가 만들어진 뒤 이 응답에 덧붙는다.
 *
 * <p>{@code owners} 는 온체인 잔액 Projection 이 채워지기 전까지 빈 목록이다.
 * 각 항목의 {@code syncedAt} 이 "언제 기준 값인가" 를 말해 준다.
 */
public record MaterialDetailResponse(
        // 기본정보
        Long materialId,
        String materialCode,
        MaterialType materialType,
        MaterialRootOrigin rootOrigin,
        MaterialGenerationType generationType,
        String name,
        String description,
        String grade,
        Long lengthMm,
        Long widthMm,
        Long registrantId,
        LocalDateTime createdAt,

        // 수량 (gram 정수)
        long totalQuantity,

        // Owner
        List<MaterialOwnerResponse> owners,

        // Evidence
        String evidenceHash,
        String manifestUrl,

        // Verification — 조회 시점 계산값. 판정 범위는 위 주석 참고
        MaterialVerificationState verificationState,

        // 상태
        MaterialStatus status,

        // 온체인 매핑. confirm 전까지 전부 null
        Long tokenId,
        Long chainId,
        String contractAddress) {

    public static MaterialDetailResponse of(
            Material material,
            List<MaterialBalance> balances,
            MaterialVerificationState verificationState) {
        return new MaterialDetailResponse(
                material.getId(),
                material.getMaterialCode(),
                material.getMaterialType(),
                material.getRootOrigin(),
                material.getGenerationType(),
                material.getName(),
                material.getDescription(),
                material.getGrade(),
                material.getLengthMm(),
                material.getWidthMm(),
                material.getRegistrantId(),
                material.getCreatedAt(),
                material.getTotalQuantity(),
                balances.stream().map(MaterialOwnerResponse::from).toList(),
                material.getEvidenceHash(),
                material.getManifestUrl(),
                verificationState,
                material.getStatus(),
                material.getTokenId(),
                material.getChainId(),
                material.getContractAddress());
    }
}
