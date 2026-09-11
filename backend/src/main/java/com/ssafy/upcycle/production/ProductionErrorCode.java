package com.ssafy.upcycle.production;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 생산 배치의 실패 사유.
 *
 * <p>Bean Validation 실패(필수 필드 누락, 형식 오류)는 {@code GlobalExceptionHandler} 가
 * 공통 봉투로 400 을 내주므로 여기에 두지 않는다. 여기 있는 것은 <b>요청 형식은 맞지만
 * 도메인 규칙에 걸리는</b> 경우뿐이다.
 */
public enum ProductionErrorCode implements ErrorCode {

    PRODUCTION_BATCH_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PRODUCTION_BATCH_NOT_FOUND", "생산 배치를 찾을 수 없습니다."),

    /** 남의 배치를 건드리려는 경우. 역할만 보면 브랜드 아무나 남의 생산에 소재를 넣을 수 있다. */
    PRODUCTION_FORBIDDEN(
            HttpStatus.FORBIDDEN, "PRODUCTION_FORBIDDEN", "본인이 만든 생산 배치만 관리할 수 있습니다."),

    /**
     * 작성 중이 아닌 배치의 투입을 바꾸려는 경우.
     *
     * <p>서명이 시작되면 투입을 바꿀 수 없다 — 서명한 내용과 저장된 내용이 달라지면
     * 온체인 기록이 무엇을 증명하는지 알 수 없게 된다.
     */
    PRODUCTION_BATCH_NOT_EDITABLE(
            HttpStatus.CONFLICT, "PRODUCTION_BATCH_NOT_EDITABLE", "작성 중인 배치만 수정할 수 있습니다."),

    PRODUCTION_PRODUCER_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PRODUCTION_PRODUCER_NOT_FOUND", "생산자를 찾을 수 없습니다."),

    PRODUCTION_PRODUCER_SUSPENDED(
            HttpStatus.FORBIDDEN, "PRODUCTION_PRODUCER_SUSPENDED", "정지된 계정입니다."),

    PRODUCTION_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN, "PRODUCTION_ROLE_NOT_ALLOWED", "생산은 브랜드만 할 수 있습니다."),

    /**
     * 지갑이 연결되지 않은 계정이 배치를 열려는 경우.
     *
     * <p>{@code producer_wallet} 이 {@code NOT NULL} 인 것은 확정 시 서명할 주소를 <b>배치를
     * 여는 시점에 고정</b>하기 때문이다. 나중에 다시 읽으면 지갑을 바꾼 뒤에는 다른 주소가
     * 나오고, 온체인 기록과 DB 가 어긋난다.
     */
    PRODUCTION_WALLET_NOT_LINKED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCTION_WALLET_NOT_LINKED",
            "지갑을 먼저 연결해야 생산을 시작할 수 있습니다."),

    PRODUCTION_PRODUCT_MODEL_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PRODUCTION_PRODUCT_MODEL_NOT_FOUND", "제품을 찾을 수 없습니다."),

    /** 남의 제품을 생산하려는 경우. */
    PRODUCTION_PRODUCT_MODEL_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "PRODUCTION_PRODUCT_MODEL_FORBIDDEN",
            "본인이 등록한 제품만 생산할 수 있습니다."),

    PRODUCTION_MATERIAL_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PRODUCTION_MATERIAL_NOT_FOUND", "소재를 찾을 수 없습니다."),

    /**
     * 보유하지 않은 소재를 투입하려는 경우.
     *
     * <p><b>수량 부족과 구분한다.</b> 하나로 합치면 "수량이 부족하다" 는 안내를 받은
     * 사용자가 수량을 줄여 가며 다시 시도하는데, 애초에 가진 적이 없는 소재라 아무리 줄여도
     * 통과하지 못한다. 판매 등록이 같은 이유로 같은 구분을 한다
     * ({@code LISTING_MATERIAL_NOT_OWNED}).
     *
     * <p><b>보유량이 0 인 것과도 다르다.</b> 전량을 소비한 소재는 {@code material_balance}
     * 행이 남아 있고, 그 경우의 정직한 답은 "수량이 부족하다" 다.
     */
    PRODUCTION_MATERIAL_NOT_OWNED(
            HttpStatus.FORBIDDEN, "PRODUCTION_MATERIAL_NOT_OWNED", "보유하지 않은 소재입니다."),

    /**
     * 가공을 거치지 않은 폐소재를 투입하려는 경우.
     *
     * <p>ERD 의 투입 자격 —
     * {@code NOT (root_origin = 'WASTE' AND generation_type = 'INITIAL')}.
     * 수거한 그대로의 폐소재는 제품이 될 수 없고 Processing 을 최소 1회 거쳐야 한다.
     * 이걸 막지 않으면 <b>가공 이력이 없는 제품이 업사이클로 팔린다.</b>
     */
    PRODUCTION_MATERIAL_NOT_ELIGIBLE(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCTION_MATERIAL_NOT_ELIGIBLE",
            "가공을 거치지 않은 폐소재는 생산에 투입할 수 없습니다."),

    /**
     * 한 요청 안에 같은 소재가 두 번 들어 있는 경우.
     *
     * <p>{@link #PRODUCTION_MATERIAL_ALREADY_INPUT} 과 다르다 — 그쪽은 이미 배치에 들어가
     * 있는 소재를 또 넣으려는 경우이고, 이쪽은 <b>이번 요청 안에서</b> 겹치는 경우다.
     * DB 의 UNIQUE 는 두 번째 INSERT 에서야 걸리는데, 그때는 첫 번째 잠금이 이미 만들어진
     * 뒤다. 쓰기 전에 걸러야 그 경로가 아예 없어진다.
     *
     * <p>수량을 합쳐서 하나로 만들지 않는다. 사용자가 실수로 두 번 고른 것인지 두 덩이를
     * 따로 세려던 것인지 알 수 없고, 합치면 화면의 목록과 저장된 내용이 달라진다.
     */
    PRODUCTION_DUPLICATE_MATERIAL_IN_REQUEST(
            HttpStatus.BAD_REQUEST,
            "PRODUCTION_DUPLICATE_MATERIAL_IN_REQUEST",
            "같은 소재를 한 번에 두 번 투입할 수 없습니다."),

    /**
     * 같은 소재를 한 배치에 두 번 넣으려는 경우.
     *
     * <p>DB 의 UNIQUE {@code (production_batch_id, material_id)} 가 막지만 그건
     * {@code DataIntegrityViolationException} 이라 사용자에게 설명할 수 없다. 수량을 바꾸려면
     * 기존 투입을 빼고 다시 넣는다.
     */
    PRODUCTION_MATERIAL_ALREADY_INPUT(
            HttpStatus.CONFLICT,
            "PRODUCTION_MATERIAL_ALREADY_INPUT",
            "이미 투입된 소재입니다. 수량을 바꾸려면 투입을 취소하고 다시 넣어 주세요."),

    /**
     * 투입량 셋의 합이 총 투입량과 다른 경우.
     *
     * <p>{@code input = 제품사용 + 재사용자투리 + 폐기}. 엔티티도 같은 검사를 하지만 그쪽은
     * {@code IllegalArgumentException} 이라 그대로 두면 500 이 나간다. 사용자가 고칠 수 있는
     * 입력 오류이므로 도메인 오류로 바꿔 400 을 만든다 — 엔티티 검사는 최후 방어선으로 남긴다.
     */
    PRODUCTION_INPUT_QUANTITY_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PRODUCTION_INPUT_QUANTITY_MISMATCH",
            "투입량은 제품 사용량 + 재사용 자투리 + 폐기량과 같아야 합니다."),

    /** 잠금을 뺀 가용 수량이 모자라는 경우. 보유량이 아니라 가용량으로 판단한다. */
    PRODUCTION_INSUFFICIENT_BALANCE(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCTION_INSUFFICIENT_BALANCE",
            "사용 가능한 수량이 부족합니다."),

    /**
     * 투입이 하나도 없는 배치를 확정하려는 경우.
     *
     * <p>소재 없이 만들어진 제품은 추적할 이력이 없다. QR 을 찍어도 보여 줄 것이 없는
     * 제품이 마켓에 올라가는 것을 막는다.
     */
    PRODUCTION_NO_INPUT(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCTION_NO_INPUT",
            "투입된 소재가 없습니다. 소재를 연결한 뒤 확정해 주세요."),

    /**
     * 투입은 있는데 제품에 들어간 양이 0 인 경우.
     *
     * <p>전량이 자투리·폐기라는 뜻이고, 그런 배치는 제품을 만들지 못한다. 업사이클 비율도
     * {@code 0/0} 이 되어 계산할 수 없다.
     */
    PRODUCTION_NO_PRODUCT_MATERIAL(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCTION_NO_PRODUCT_MATERIAL",
            "제품에 사용된 소재가 없습니다."),

    /**
     * 투입이 가리키는 잠금이 더 이상 유효하지 않은 경우.
     *
     * <p>확정 시점에 잠금이 이미 풀렸거나 소비됐다는 뜻이며, <b>데이터가 어긋났다는
     * 신호다</b>. 조용히 넘어가면 잠기지 않은 소재를 소비하게 되고 잔량이 음수가 된다.
     */
    PRODUCTION_LOCK_NOT_ACTIVE(
            HttpStatus.CONFLICT,
            "PRODUCTION_LOCK_NOT_ACTIVE",
            "투입에 걸린 잠금이 유효하지 않습니다. 투입을 다시 확인해 주세요."),

    /**
     * 확정되지 않은 배치를 온체인에 기록하려는 경우.
     *
     * <p>온체인 기록은 "이 제품이 이 소재로 만들어졌다" 는 증명이다. 아직 확정되지 않은
     * 배치는 그 사실이 성립하지 않았고, 투입이 더 바뀔 수도 있다 — 바뀐 뒤에도 체인에는
     * 옛 내용의 해시가 남는다.
     */
    PRODUCTION_BATCH_NOT_CONFIRMED(
            HttpStatus.CONFLICT,
            "PRODUCTION_BATCH_NOT_CONFIRMED",
            "확정된 배치만 온체인에 기록할 수 있습니다."),

    /**
     * 화면이 보낸 해시가 서버가 계산한 manifest 해시와 다른 경우.
     *
     * <p>화면이 <b>서버가 모르는 내용</b>을 체인에 올렸다는 뜻이다. 그 해시는 나중에 재현할
     * 수 없고, 재현할 수 없는 해시는 아무것도 증명하지 못한다 — 추적성의 마지막 한 칸이
     * 그 대조에 걸려 있다.
     *
     * <p>해시는 {@code GET /{id}/chain/manifest} 가 준다. 화면이 직접 계산하지 않는다 —
     * 계산 주체가 둘이면 필드 순서·공백·인코딩 차이로 언젠가 갈라진다.
     */
    PRODUCTION_MANIFEST_HASH_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PRODUCTION_MANIFEST_HASH_MISMATCH",
            "서버가 만든 manifest 해시와 다릅니다. 최신 manifest 를 다시 받아 서명해 주세요."),

    /**
     * 투입 소재 중 온체인 발행이 안 된 것이 있는 경우.
     *
     * <p>{@code submitPlanAndLock} 이 {@code tokenIds[]} 를 인자로 받는데, 발행되지 않은
     * 소재에는 그 번호가 없다. 순서를 강제하지 않으면 화면이 <b>존재하지 않는 토큰 번호로
     * 서명을 시도</b>하고 사용자는 지갑 팝업까지 간 뒤에 revert 를 본다 — 거래 도메인이
     * 같은 이유로 같은 검사를 한다.
     */
    PRODUCTION_MATERIAL_NOT_ON_CHAIN(
            HttpStatus.CONFLICT,
            "PRODUCTION_MATERIAL_NOT_ON_CHAIN",
            "온체인 발행되지 않은 소재가 있습니다. 소재를 먼저 발행해 주세요."),

    /**
     * 온체인 잠금 번호가 아직 연결되지 않은 경우.
     *
     * <p>{@code submitPlanAndLock} 이 만든 잠금 번호를 2단계 보고에서 받아 둔다. 그것 없이
     * 소각을 보고하면 <b>어느 온체인 잠금이 소각됐는지</b> 서버가 확인할 수 없다.
     */
    PRODUCTION_CHAIN_LOCKS_NOT_LINKED(
            HttpStatus.CONFLICT,
            "PRODUCTION_CHAIN_LOCKS_NOT_LINKED",
            "온체인 잠금을 먼저 연결해야 합니다."),

    /**
     * 온체인 배치 번호가 아직 연결되지 않은 경우.
     *
     * <p>{@code createBatch} 가 채번한 번호 없이는 {@code submitPlanAndLock} 을 부를 인자가
     * 없다. 순서를 강제하지 않으면 화면이 <b>존재하지 않는 온체인 번호로 서명을 시도</b>하고,
     * 사용자는 지갑 팝업까지 간 뒤에 revert 를 본다 (거래 도메인이 같은 이유로 같은 검사를 한다).
     */
    PRODUCTION_CHAIN_BATCH_NOT_LINKED(
            HttpStatus.CONFLICT,
            "PRODUCTION_CHAIN_BATCH_NOT_LINKED",
            "온체인 배치를 먼저 생성하고 연결해야 합니다."),

    /**
     * 이미 온체인 배치 번호가 연결된 경우.
     */
    PRODUCTION_CHAIN_BATCH_ALREADY_LINKED(
            HttpStatus.CONFLICT,
            "PRODUCTION_CHAIN_BATCH_ALREADY_LINKED",
            "이미 온체인 배치가 연결되어 있습니다."),

    /**
     * 이미 온체인에 기록된 배치를 다시 기록하려는 경우.
     *
     * <p>두 번 기록되면 어느 해시가 정본인지 알 수 없다. 다시 올려야 한다면 그건 새 기록이
     * 아니라 정정이고, 정정은 이력을 덮어쓰는 일이라 별도 절차가 필요하다.
     */
    PRODUCTION_BATCH_ALREADY_ON_CHAIN(
            HttpStatus.CONFLICT,
            "PRODUCTION_BATCH_ALREADY_ON_CHAIN",
            "이미 온체인에 기록된 배치입니다."),

    /**
     * 보고한 소각이 배치의 투입과 짝이 맞지 않는 경우.
     *
     * <p>확정은 투입 전체를 한꺼번에 소비한다. 그중 일부만 소각하고 보고하면 DB 는 다
     * 썼다고 하는데 체인에는 남아 있는 소재가 생기고, 그 소재로 또 만들었다고 주장할 수
     * 있다. 반대로 없는 투입을 보고하면 그 잠금이 무엇인지 서버가 설명할 수 없다.
     */
    PRODUCTION_CONSUME_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PRODUCTION_CONSUME_MISMATCH",
            "소각한 잠금이 이 배치의 투입과 일치하지 않습니다."),

    /**
     * 이미 소각이 기록된 배치를 다시 보고하는 경우.
     *
     * <p>두 번 기록되면 같은 소재를 두 번 소각한 것처럼 보인다.
     */
    PRODUCTION_CONSUME_ALREADY_RECORDED(
            HttpStatus.CONFLICT,
            "PRODUCTION_CONSUME_ALREADY_RECORDED",
            "이미 소각이 기록된 배치입니다."),

    /**
     * 이 배치의 개체가 아닌 것을 가리키는 경우.
     *
     * <p>개체 번호만 믿으면 <b>남의 배치 개체 라벨을 받아 갈 수 있다</b>. 배치 소유자
     * 검사를 통과했더라도 개체가 그 배치의 것인지는 따로 봐야 한다.
     */
    PRODUCTION_ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PRODUCTION_ITEM_NOT_FOUND", "개체를 찾을 수 없습니다."),

    /**
     * QR 이미지 크기가 허용 범위를 벗어난 경우.
     *
     * <p>너무 작으면 인쇄 후 스캔이 되지 않고, 상한이 없으면 요청 하나로 수십 MB PNG 를
     * 만들 수 있다. 생성 모듈도 같은 검사를 하지만 그쪽은
     * {@code IllegalArgumentException} 이라 그대로 두면 500 이 나간다 — 사용자가 고칠 수
     * 있는 입력이므로 400 으로 바꾼다.
     */
    PRODUCTION_QR_SIZE_INVALID(
            HttpStatus.BAD_REQUEST,
            "PRODUCTION_QR_SIZE_INVALID",
            "QR 크기는 128 이상 2048 이하여야 합니다."),

    PRODUCTION_INPUT_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PRODUCTION_INPUT_NOT_FOUND", "투입 내역을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ProductionErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
