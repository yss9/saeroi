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
}
