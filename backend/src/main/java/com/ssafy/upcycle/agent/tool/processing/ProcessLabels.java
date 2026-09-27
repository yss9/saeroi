package com.ssafy.upcycle.agent.tool.processing;

import com.ssafy.upcycle.processing.entity.ProcessType;
import java.util.Collection;
import java.util.stream.Collectors;

/** 공정의 한글 이름. 진행 막대와 카드 제목이 같은 말을 쓴다. */
public final class ProcessLabels {

    private ProcessLabels() {}

    public static String text(ProcessType type) {
        return switch (type) {
            case CLEANING -> "세척";
            case DISASSEMBLY -> "해체";
            case SORTING -> "선별";
            case CUTTING -> "재단";
            case SHREDDING -> "파쇄";
            case COLOR_PROCESSING -> "염색";
            case SURFACE_FINISHING -> "표면처리";
            case REMANUFACTURING -> "재제조";
        };
    }

    /** "세척·해체·선별" */
    public static String joined(Collection<ProcessType> types) {
        return types.stream().map(ProcessLabels::text).collect(Collectors.joining("·"));
    }
}
