package com.ssafy.upcycle.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SingleLineValidator implements ConstraintValidator<SingleLine, String> {

    /**
     * {@code null} 은 통과시킨다 — 필수 여부는 {@code @NotBlank}/{@code @NotNull} 의 몫이고,
     * 두 제약이 같은 필드에 같은 이유로 실패하면 오류가 두 줄 나간다.
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.chars().noneMatch(Character::isISOControl);
    }
}
