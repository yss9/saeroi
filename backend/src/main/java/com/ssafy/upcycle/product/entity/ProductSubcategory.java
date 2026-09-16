package com.ssafy.upcycle.product.entity;

/**
 * 제품 하위 분류. 소비자 SHOP 의 2단 메뉴가 이 값으로 갈린다.
 *
 * <p><b>{@link ProductCategory} 를 늘리지 않고 컬럼을 따로 둔다.</b> 기존 분류는 이미 팔린
 * 주문과 여권(passport)에 그대로 실려 있어, {@code CLOTHING} 을 {@code CLOTHING_TOP} 같은
 * 값들로 쪼개면 그 이력이 가리키던 분류가 사라진다. 상위는 그대로 두고 아래를 덧붙이는
 * 편이 기존 데이터를 건드리지 않는다.
 *
 * <p><b>모든 상위 분류에 하위가 있는 것은 아니다.</b> {@code WALLET}·{@code ACCESSORY}·
 * {@code HOME}·{@code ETC} 는 2단 메뉴가 없어 언제나 {@code null} 이다. 그래서 이 값은
 * 필수가 아니며, 하위 분류를 지정하지 않은 기존 제품도 상위 분류 목록에는 그대로 나온다.
 *
 * <p>{@code category} 와 마찬가지로 DB 는 {@code VARCHAR(50)} 이다 — 값을 늘려도 스키마
 * 마이그레이션이 필요 없고, 오타는 이 자바 enum 이 막는다.
 */
public enum ProductSubcategory {

    TOP(ProductCategory.CLOTHING),
    OUTER(ProductCategory.CLOTHING),
    PANTS(ProductCategory.CLOTHING),
    SKIRT(ProductCategory.CLOTHING),
    DRESS(ProductCategory.CLOTHING),

    TOTE(ProductCategory.BAG),
    SHOULDER(ProductCategory.BAG),
    CROSSBODY(ProductCategory.BAG),
    BACKPACK(ProductCategory.BAG);

    private final ProductCategory category;

    ProductSubcategory(ProductCategory category) {
        this.category = category;
    }

    /** 이 하위 분류가 속한 상위 분류. */
    public ProductCategory category() {
        return category;
    }

    /**
     * 주어진 상위 분류에 속하는가.
     *
     * <p>하위가 없는 상위 분류({@code WALLET} 등)에는 어떤 값도 속하지 않으므로 언제나
     * {@code false} 다 — "가방인데 하위 분류가 원피스" 같은 조합이 만들어지지 않는다.
     */
    public boolean belongsTo(ProductCategory category) {
        return this.category == category;
    }
}
