package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialStatus;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.entity.MaterialComposition;
import com.ssafy.upcycle.material.entity.MaterialOriginReason;
import com.ssafy.upcycle.material.entity.MaterialVerificationState;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소재 상세. 티켓이 요구한 6블록 — 기본정보 · 수량 · Owner · Evidence · Verification · 상태.
 *
 * <h2>{@code verificationState} 가 판정하는 범위</h2>
 *
 * <p>저장 컬럼이 아니라 {@code MaterialVerificationStateResolver} 가 조회 시점에 계산한
 * 값이다. 지금 판정되는 단계는 {@code REGISTERED} · {@code SOURCE_VERIFIED} ·
 * {@code TRANSACTION_VERIFIED} 셋이다 ({@code docs/superpowers/specs/2026-09-07-material-verification-state.md}).
 *
 * <p>남은 두 단({@code PROCESSING_VERIFIED}·{@code PRODUCT_LINKED})은 판정 근거가 될
 * {@code processing_job}·{@code production_input} 엔티티가 아직 없다. 그래서 <b>결과
 * 자체는 오늘 기준으로 정확하다.</b> 도달할 수 없는 단계를 놓치는 것이지 틀리게
 * 보고하는 것이 아니다.
 *
 * <p>{@code verification} 은 {@code SOURCE_VERIFIED} 일 때만 근거(지갑·Tx 해시·시각)를
 * 채운 채 내려온다. 다른 단은 근거의 종류가 달라(거래 서명 등) 이 모양이 맞지 않으므로
 * {@code null} 이다 — {@link MaterialVerificationResponse} 주석 참고.
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

        // 왜 폐기물이 되었나. 신소재면 둘 다 null 이다 — 그 상태가 정상이다
        MaterialOriginReason originReason,
        String originReasonNote,

        // 구성 성분. 정보가 없으면 **빈 배열**이고 null 이 아니다 —
        // 화면이 "없음" 과 "모름" 을 두 번 분기하지 않게 한다
        List<MaterialCompositionResponse> composition,

        // Evidence
        String evidenceHash,
        String manifestUrl,

        // Verification — 조회 시점 계산값. 판정 범위는 위 주석 참고
        MaterialVerificationState verificationState,
        MaterialVerificationResponse verification,

        // 상태
        MaterialStatus status,

        // 온체인 매핑. confirm 전까지 전부 null
        Long tokenId,
        Long chainId,
        String contractAddress) {

    public static MaterialDetailResponse of(
            Material material,
            List<MaterialBalance> balances,
            List<MaterialComposition> composition,
            MaterialVerificationState verificationState,
            MaterialVerificationResponse verification) {
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
                material.getOriginReason(),
                material.getOriginReasonNote(),
                composition.stream().map(MaterialCompositionResponse::from).toList(),
                material.getEvidenceHash(),
                material.getManifestUrl(),
                verificationState,
                verification,
                material.getStatus(),
                material.getTokenId(),
                material.getChainId(),
                material.getContractAddress());
    }
}
