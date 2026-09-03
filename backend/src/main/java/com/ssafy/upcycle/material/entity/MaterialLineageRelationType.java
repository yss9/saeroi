package com.ssafy.upcycle.material.entity;

/**
 * 부모 소재에서 자식 소재가 나온 경로.
 *
 * <p>{@code reference_id} 가 가리키는 대상이 값마다 다르다 —
 * {@link #PROCESSING} 은 processing_job, {@link #PRODUCTION_SCRAP} 은 production_batch 다.
 */
public enum MaterialLineageRelationType {

    PROCESSING,
    PRODUCTION_SCRAP
}
