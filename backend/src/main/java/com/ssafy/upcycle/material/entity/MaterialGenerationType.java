package com.ssafy.upcycle.material.entity;

/**
 * 소재가 어떤 경로로 생겨났는지.
 *
 * <p>{@link MaterialRootOrigin} 과 축이 다르다. WASTE 에서 나온 소재도 가공을 거치면
 * {@link #PROCESSED} 가 되지만 출처는 여전히 WASTE 다. 두 값을 한 컬럼에 섞으면
 * "가공된 폐소재"와 "가공된 새 원단"을 구분할 수 없다.
 *
 * <p>이름에 {@code Material} 접두사가 붙은 이유는 {@code jakarta.persistence.GenerationType} 과
 * 단순 이름이 같아서다. 엔티티는 {@code @GeneratedValue} 때문에 그쪽도 함께 참조해야 하는데,
 * 단일 타입 import 가 같은 패키지의 타입을 가려버려 둘 중 하나는 FQN 을 써야 한다.
 */
public enum MaterialGenerationType {

    /** 플랫폼에 최초로 등록된 소재. */
    INITIAL,

    /** Processing 을 거쳐 생성된 소재. */
    PROCESSED,

    /** Production 에서 나온 재사용 가능한 자투리. */
    PRODUCTION_SCRAP
}
