package com.ssafy.upcycle.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 한 줄로 표시되는 값 — 줄바꿈·탭 같은 제어문자를 받지 않는다.
 *
 * <p><b>왜 필요한가.</b> {@code @NotBlank} 는 앞뒤 공백만 보고 <b>가운데 낀 {@code \n} 은
 * 통과시킨다.</b> 이름에 {@code "줄바꿈\n이름"} 이 저장되면 목록·배송 송장·CSV 처럼 한 줄을
 * 전제한 자리에서 레이아웃이 깨지고, 값이 잘려 보이기도 한다. 화면에서 지워도 DB 에는
 * 남아 있어 원인을 찾기 어렵다 (2026-09-21 확인).
 *
 * <p><b>공백 자체는 막지 않는다.</b> 이름과 주소에는 보통의 스페이스가 당연히 들어간다.
 * 막는 것은 {@link Character#isISOControl 제어문자}뿐이다.
 *
 * <p>길이·필수 여부는 이 제약이 보지 않는다. {@code @Size}·{@code @NotBlank} 와 함께 쓴다.
 */
@Documented
@Constraint(validatedBy = SingleLineValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SingleLine {

    String message() default "줄바꿈이나 제어문자는 사용할 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
