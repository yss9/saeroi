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

    PRODUCT_ORDER_BUYER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_ORDER_BUYER_NOT_FOUND",
            "주문자를 찾을 수 없습니다."),

    PRODUCT_ORDER_BUYER_SUSPENDED(
            HttpStatus.FORBIDDEN,
            "PRODUCT_ORDER_BUYER_SUSPENDED",
            "정지된 계정입니다."),

    PRODUCT_ORDER_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "PRODUCT_ORDER_ROLE_NOT_ALLOWED",
            "소비자 계정만 제품 주문을 조회할 수 있습니다."),

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

    /**
     * 바꿀 내용이 없거나 빈 값을 보낸 수정 요청.
     *
     * <p>부분 수정이라 "안 보냄"({@code null})과 "빈 값으로 바꿈"({@code ""})을 구분해야
     * 하는데, 이름을 빈 문자열로 만드는 것은 실수이지 의도가 아니다. 아무것도 안 보낸
     * 요청도 마찬가지다 — 성공했다고 답할 것이 없다.
     */
    PRODUCT_MODEL_NOTHING_TO_UPDATE(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_MODEL_NOTHING_TO_UPDATE",
            "수정할 내용을 올바르게 지정해 주세요."),

    /**
     * 상위 분류에 속하지 않는 하위 분류를 보낸 경우.
     *
     * <p>{@code BAG} 인데 {@code DRESS} 같은 조합이다. Bean Validation 으로는 두 필드의
     * 관계를 볼 수 없어 여기 둔다 — 형식은 맞지만 도메인 규칙에 걸리는 경우다.
     *
     * <p>하위 분류가 아예 없는 분류({@code WALLET}·{@code ACCESSORY}·{@code HOME}·
     * {@code ETC})에 값을 보낸 경우도 여기 걸린다.
     */
    PRODUCT_SUBCATEGORY_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_SUBCATEGORY_NOT_ALLOWED",
            "선택한 카테고리에 없는 하위 분류입니다."),

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

    /**
     * 갤러리 교체 요청이 준 저장 키가 저장소에 없는 경우.
     *
     * <p>{@code PRODUCT_THUMBNAIL_NOT_STORED} 와 문구가 같지만 코드를 나눈다 — 화면이
     * 대표 이미지 입력과 갤러리 목록 중 어느 쪽을 가리켜야 할지 답에서 알 수 있어야 한다.
     */
    PRODUCT_IMAGE_NOT_STORED(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_IMAGE_NOT_STORED",
            "업로드되지 않은 이미지입니다. 이미지를 먼저 올려 주세요."),

    /** 한 갤러리에 같은 이미지를 두 번 넣은 경우. 같은 사진이 두 칸을 차지할 이유가 없다. */
    PRODUCT_IMAGE_DUPLICATED(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_IMAGE_DUPLICATED",
            "같은 이미지를 두 번 등록할 수 없습니다."),

    /**
     * 대표 사진을 둘 이상 지정한 경우.
     *
     * <p>없는 것은 허용한다 — 그때는 첫 자리가 대표 자리다. 둘이면 어느 쪽이 대표인지
     * 서버가 답할 수 없고, 화면마다 다른 사진을 고르게 된다.
     */
    PRODUCT_IMAGE_MAIN_NOT_UNIQUE(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_IMAGE_MAIN_NOT_UNIQUE",
            "대표 이미지는 한 장만 지정할 수 있습니다."),

    /** 갤러리 장수 상한을 넘긴 경우. 공개 상세 응답이 요청 하나로 커지는 것을 막는다. */
    PRODUCT_IMAGE_TOO_MANY(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_IMAGE_TOO_MANY",
            "상품 이미지는 최대 10장까지 등록할 수 있습니다."),

    /** 그 제품의 갤러리에 없는 이미지 id. 남의 제품 사진을 가리킨 경우도 이것이다. */
    PRODUCT_IMAGE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_IMAGE_NOT_FOUND",
            "상품 이미지를 찾을 수 없습니다."),

    /** 갤러리 행은 있는데 그 파일이 저장소에서 사라진 경우. */
    PRODUCT_IMAGE_FILE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_IMAGE_FILE_NOT_FOUND",
            "상품 이미지 파일을 찾을 수 없습니다."),

    /** 제품 행은 있는데 그 이미지 파일이 저장소에서 사라진 경우. */
    PRODUCT_THUMBNAIL_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_THUMBNAIL_NOT_FOUND",
            "제품 이미지를 찾을 수 없습니다."),

    PRODUCT_MODEL_HAS_NO_STOCK(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCT_MODEL_HAS_NO_STOCK",
            "판매할 재고가 없습니다. 생산이 확정되어야 판매를 시작할 수 있습니다."),

    /**
     * 살 수 있는 상태가 아닌 제품을 주문하려는 경우.
     *
     * <p>{@code DRAFT} 는 아직 노출되지 않은 제품이고, {@code DISCONTINUED} 는 남은 개체가
     * 있어도 브랜드가 더 팔지 않기로 한 상태다. 재고만 보고 통과시키면 내린 제품이
     * 계속 팔린다.
     */
    PRODUCT_ORDER_MODEL_NOT_ON_SALE(
            HttpStatus.CONFLICT,
            "PRODUCT_ORDER_MODEL_NOT_ON_SALE",
            "현재 구매할 수 없는 제품입니다."),

    /**
     * 주문 순간 남은 개체가 없는 경우.
     *
     * <p>{@code PRODUCT_MODEL_HAS_NO_STOCK} 과 나누어 둔다 — 그쪽은 브랜드가 판매를
     * 시작하려할 때의 안내라 문구가 판매자용이다. 소비자에게는 "품절" 이어야 한다.
     */
    PRODUCT_ORDER_OUT_OF_STOCK(
            HttpStatus.CONFLICT,
            "PRODUCT_ORDER_OUT_OF_STOCK",
            "품절되었습니다."),

    /**
     * 여러 상품을 한 번에 주문했는데 그중 일부의 재고가 모자란 경우.
     *
     * <p><b>되는 것만 주문하지 않고 전부 실패시킨다.</b> 일부만 담기면 사용자가 기대한 것과
     * 다른 금액이 결제되고 그건 분쟁이 된다. 결제 전에 걸러지므로 환불도 필요 없다.
     *
     * <p>어떤 상품이 모자란지는 응답의 {@code data} 에 {@code OutOfStockLine} 목록으로
     * 실린다 — 코드와 문구만으로는 사용자가 장바구니에서 무엇을 뺄지 알 수 없다.
     */
    PRODUCT_ORDER_ITEMS_OUT_OF_STOCK(
            HttpStatus.CONFLICT,
            "PRODUCT_ORDER_ITEMS_OUT_OF_STOCK",
            "일부 상품의 재고가 부족합니다."),

    /** 주문할 항목이 하나도 없는 요청. */
    PRODUCT_ORDER_NO_ITEMS(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_ORDER_NO_ITEMS",
            "주문할 상품이 없습니다."),

    /** 0 이하이거나 한 번에 담을 수 있는 수를 넘긴 수량. */
    PRODUCT_ORDER_QUANTITY_INVALID(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_ORDER_QUANTITY_INVALID",
            "주문 수량이 올바르지 않습니다."),

    /** 본인의 배송지가 아니거나 없는 id 를 가리킨 경우. 둘을 같은 답으로 덮는다. */
    PRODUCT_ORDER_ADDRESS_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_ORDER_ADDRESS_NOT_FOUND",
            "배송지를 찾을 수 없습니다."),

    /** 남의 주문을 가리키는 경우도 이것이다 — 403 은 그 주문이 존재한다는 사실을 흘린다. */
    PRODUCT_ORDER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_ORDER_NOT_FOUND",
            "주문을 찾을 수 없습니다."),

    /**
     * 그 주문을 판 브랜드가 아닌 계정이 발송 처리를 시도한 경우.
     *
     * <p>구매자도 여기 걸린다 — 보내는 쪽은 파는 브랜드다. 주문 자체는 존재를 숨길 필요가
     * 없어 404 가 아니다 — 브랜드는 자신에게 들어온 주문을 목록에서 이미 본다.
     */
    PRODUCT_ORDER_NOT_SELLER(
            HttpStatus.FORBIDDEN,
            "PRODUCT_ORDER_NOT_SELLER",
            "본인이 판매한 주문만 발송 처리할 수 있습니다."),

    PRODUCT_ORDER_ALREADY_SHIPPED(
            HttpStatus.CONFLICT,
            "PRODUCT_ORDER_ALREADY_SHIPPED",
            "이미 발송 처리된 주문입니다."),

    /** 결제되지 않은 주문. 돈을 받기 전에 물건을 보내면 되돌릴 근거가 없다. */
    PRODUCT_ORDER_NOT_SHIPPABLE(
            HttpStatus.CONFLICT,
            "PRODUCT_ORDER_NOT_SHIPPABLE",
            "결제가 끝난 주문만 발송할 수 있습니다."),

    /** 발송되지 않은 주문을 받았다고 할 수는 없다. */
    PRODUCT_ORDER_NOT_DELIVERABLE(
            HttpStatus.CONFLICT,
            "PRODUCT_ORDER_NOT_DELIVERABLE",
            "발송된 주문만 수령 확인할 수 있습니다."),

    /** 주문은 있는데 배송지 행이 없는 경우. 정상 흐름에서는 나오지 않는다. */
    PRODUCT_ORDER_SHIPPING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PRODUCT_ORDER_SHIPPING_NOT_FOUND",
            "주문의 배송 정보를 찾을 수 없습니다."),

    /** 택배사 없는 운송장 번호는 조회할 수 없어 기록해도 쓸모가 없다. */
    PRODUCT_ORDER_TRACKING_WITHOUT_CARRIER(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_ORDER_TRACKING_WITHOUT_CARRIER",
            "운송장 번호를 입력하려면 택배사도 함께 입력해 주세요."),

    /**
     * 발송된 뒤에 취소를 시도한 경우.
     *
     * <p>결제 쪽 코드(PAYMENT_STATE_INVALID)를 쓰지 않는다. 그쪽 문구는 "지금
     * 상태에서는 처리할 수 없습니다" 라 왜 안 되는지를 말하지 않는다 — 프론트는 서버
     * 문구를 그대로 띄우므로 여기서 정확히 말해야 사용자가 목록을 다시 불러보고 납득한다.
     *
     * <p>화면은 발송 전에만 취소 버튼을 보여 주므로 이 응답은 <b>경합에서만</b> 나온다:
     * 목록을 열어 둔 사이 브랜드가 발송한 경우다.
     */
    PRODUCT_ORDER_NOT_CANCELABLE(
            HttpStatus.CONFLICT,
            "PRODUCT_ORDER_NOT_CANCELABLE",
            "배송이 시작된 주문은 취소할 수 없습니다.");

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
