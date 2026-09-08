package com.ssafy.upcycle.processing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processor_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessorProfile {
    @Id @Column(name = "user_id")
    private Long userId;
    @Column(name = "region", nullable = false, length = 100)
    private String region;

    private ProcessorProfile(Long userId, String region) {
        this.userId = userId;
        this.region = region;
    }

    public static ProcessorProfile register(Long userId, String region) {
        return new ProcessorProfile(userId, region.trim());
    }

    public void updateRegion(String region) {
        this.region = region.trim();
    }
}
