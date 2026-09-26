package com.ssafy.upcycle.processing.entity;

import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 폐소재가 제품이 되기 전에 반드시 거쳐야 하는 정제 공정.
 *
 * <p><b>규칙은 여기 한 곳에만 둔다.</b> 검사하는 곳(생산 확정)과 안내하는 곳(가공 요청)이
 * 각자 목록을 들고 있으면, 규칙이 바뀔 때 한쪽만 고쳐져 안내가 거짓말이 된다. 담당자는
 * 되지도 않을 계획을 세우고 가공비까지 낸 뒤에야 막힌다.
 *
 * <p><b>신소재는 보지 않는다.</b> 새 원단에는 해체할 것도 선별할 것도 없다. 이 규칙이
 * 지키려는 것은 폐기물의 정제 이력이지 모든 소재의 공정 이력이 아니다.
 */
public final class RequiredProcessing {

    private static final Set<ProcessType> REQUIRED =
            EnumSet.of(ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING);

    private RequiredProcessing() {}

    /** 필수 공정 전체. 목록을 보여줄 때 쓴다. */
    public static Set<ProcessType> all() {
        return REQUIRED;
    }

    /** 이 소재에 필수 규칙이 적용되는가. */
    public static boolean appliesTo(MaterialRootOrigin rootOrigin) {
        return rootOrigin == MaterialRootOrigin.WASTE;
    }

    /** 이미 거친 공정을 빼고 <b>아직 남은</b> 필수 공정. 순서는 목록에 적힌 순서를 따른다. */
    public static List<ProcessType> missing(
            MaterialRootOrigin rootOrigin, Collection<ProcessType> completed) {
        if (!appliesTo(rootOrigin)) {
            return List.of();
        }
        return REQUIRED.stream().filter(required -> !completed.contains(required)).toList();
    }

    /** 이 소재가 제품이 될 자격을 갖췄는가. */
    public static boolean satisfied(
            MaterialRootOrigin rootOrigin, Collection<ProcessType> completed) {
        return missing(rootOrigin, completed).isEmpty();
    }
}
