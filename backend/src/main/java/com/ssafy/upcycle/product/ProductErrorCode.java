package com.ssafy.upcycle.product;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 제품 등록·조회의 실패 사유.
 *
 * <p>Bean Validation 실패(필수 필드 누락, 형식 오류)는 {@code GlobalExceptionHandler} 가
 * 공통 봉투로 400 을 내주므로 여기에 두지 않는다. 여기 있는 것은 <b>요청 형식은 맞지만
 * 도메인 규칙에 걸리는</b> 경우뿐이다 — {@code MaterialErrorCode} 와 같은 기준이다.
 */
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_MODEL_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_MODEL_NOT_FOUND",
            "제품을 찾을 수 없습니다."),

    NOT_PRODUCT_MODEL_OWNER(
            HttpStatus.FORBIDDEN,
            "NOT_PRODUCT_MODEL_OWNER",
            "본인이 등록한 제품만 관리할 수 있습니다."),

    PRODUCT_BRAND_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_BRAND_NOT_FOUND",
            "등록자를 찾을 수 없습니다."),

    PRODUCT_BRAND_SUSPENDED(
            HttpStatus.FORBIDDEN,
            "PRODUCT_BRAND_SUSPENDED",
            "정지된 계정입니다."),

    /**
     * 브랜드가 아닌 계정이 제품을 올리려는 경우.
     *
     * <p>토큰의 역할 클레임이 아니라 DB 의 현재 역할로 판단한다 — 토큰은 발급 시점의
     * 스냅샷이라, 역할이 바뀐 계정이 만료 전까지 계속 등록할 수 있다.
     */
    PRODUCT_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "PRODUCT_ROLE_NOT_ALLOWED",
            "제품은 브랜드만 등록할 수 있습니다."),

    PRODUCT_MODEL_DISCONTINUED(
            HttpStatus.CONFLICT,
            "PRODUCT_MODEL_DISCONTINUED",
            "단종된 제품은 상태를 되돌릴 수 없습니다."),

    /**
     * 개체가 하나도 없는 제품을 판매 중으로 올리려는 경우.
     *
     * <p>재고는 {@code product_item} 을 세어 구하므로, 개체가 없으면 마켓에 뜨자마자
     * 살 수 없는 제품이 된다.
     */
    /** 파일이 붙지 않은 업로드 요청. */
    PRODUCT_IMAGE_MISSING(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_IMAGE_MISSING",
            "이미지 파일이 필요합니다."),

    /**
     * 이미지가 아닌 파일을 제품 사진으로 올리려는 경우.
     *
     * <p>공용 저장소 정책({@code FileContentPolicy})은 PDF 도 받는다 — 증빙에는 계량증명서
     * 같은 문서가 있기 때문이다. 제품 사진은 마켓에 {@code <img>} 로 걸리므로 그보다 좁다.
     */
    PRODUCT_IMAGE_TYPE_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_IMAGE_TYPE_NOT_ALLOWED",
            "제품 이미지는 JPG, PNG, WEBP 만 올릴 수 있습니다."),

    /**
     * 등록 요청이 준 이미지 키가 저장소에 없는 경우.
     *
     * <p>이 검사가 없으면 프론트가 아무 문자열이나 보내도 등록이 성공하고, 나중에 이미지가
     * 깨진 제품으로 드러난다. 그때는 이미 마켓에 올라가 있다.
     */
    PRODUCT_THUMBNAIL_NOT_STORED(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_THUMBNAIL_NOT_STORED",
            "업로드되지 않은 이미지입니다. 이미지를 먼저 올려 주세요."),

    /** 제품 행은 있는데 그 이미지 파일이 저장소에서 사라진 경우. */
    PRODUCT_THUMBNAIL_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_THUMBNAIL_NOT_FOUND",
            "제품 이미지를 찾을 수 없습니다."),

    PRODUCT_MODEL_HAS_NO_STOCK(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCT_MODEL_HAS_NO_STOCK",
            "판매할 재고가 없습니다. 생산이 확정되어야 판매를 시작할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ProductErrorCode(HttpStatus status, String code, String message) {
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
