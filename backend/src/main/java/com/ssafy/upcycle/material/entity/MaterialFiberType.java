package com.ssafy.upcycle.material.entity;

import java.util.Locale;
import java.util.Set;

/**
 * {@code material_composition.fiber_type} 의 허용 목록.
 *
 * <p><b>DB ENUM 이 아니라 코드 상수인 것이 의도다.</b> 섬유 종류는 계속 늘어나는데
 * ENUM 이면 값 추가마다 DDL 이고, {@code ddl-auto: validate} 환경에서 그건 배포 절차를
 * 부른다. 그래서 컬럼은 {@code VARCHAR(50)} 으로 두고 <b>강제는 여기서</b> 한다.
 *
 * <p>강제하지 않으면 {@code grade}({@code VARCHAR(50)})에서 이미 안고 있는 문제가
 * 그대로 온다 — 같은 뜻을 "면 · 코튼 · COTTON · Cotton" 으로 적으면 집계가 갈라진다.
 *
 * <p>값을 추가하는 것은 DDL 없이 되지만 <b>지우는 것은 안 된다</b> — 이미 저장된 행이
 * 검증을 통과하지 못하게 된다. 쓰지 않게 된 값은 목록에 남긴다.
 */
public final class MaterialFiberType {

    private static final Set<String> ALLOWED = Set.of(
            "COTTON",
            "POLYESTER",
            "NYLON",
            "WOOL",
            "LINEN",
            "RAYON",
            "ACRYLIC",
            "SPANDEX",
            "LYOCELL",
            "LEATHER",
            "DENIM",
            "OTHER");

    private MaterialFiberType() {}

    /** 대소문자·공백을 무시하고 판정한다. 저장은 대문자로 통일된다. */
    public static boolean isAllowed(String fiberType) {
        if (fiberType == null || fiberType.isBlank()) {
            return false;
        }
        return ALLOWED.contains(fiberType.trim().toUpperCase(Locale.ROOT));
    }

    /** 오류 메시지에 그대로 넣는다 — 사용자가 무엇을 쓸 수 있는지 알아야 고칠 수 있다. */
    public static Set<String> allowed() {
        return ALLOWED;
    }
}
