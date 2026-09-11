package com.ssafy.upcycle.processing.entity;

import com.ssafy.upcycle.material.entity.MaterialType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processing_output")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingOutput {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "processing_job_id", nullable = false)
    private Long processingJobId;

    @Column(name = "processing_input_id", nullable = false)
    private Long processingInputId;

    @Column(name = "output_material_id")
    private Long outputMaterialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 50)
    private MaterialType materialType;

    @Column(nullable = false, length = 50)
    private String grade;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private long quantity;

    private ProcessingOutput(
            Long processingJobId,
            Long processingInputId,
            MaterialType materialType,
            String name,
            String grade,
            long quantity) {
        if (processingJobId == null || processingInputId == null || materialType == null
                || name == null || name.isBlank() || name.length() > 200
                || grade == null || grade.isBlank() || grade.length() > 50
                || quantity <= 0) {
            throw new IllegalArgumentException("Valid processing output fields are required");
        }
        this.processingJobId = processingJobId;
        this.processingInputId = processingInputId;
        this.materialType = materialType;
        this.name = name;
        this.grade = grade;
        this.quantity = quantity;
    }

    public static ProcessingOutput submit(
            Long processingJobId,
            Long processingInputId,
            MaterialType materialType,
            String name,
            String grade,
            long quantity) {
        return new ProcessingOutput(
                processingJobId, processingInputId, materialType, name, grade, quantity);
    }

    public void linkMaterial(Long materialId) {
        if (outputMaterialId != null) {
            throw new IllegalStateException("Processing output already links a material");
        }
        if (materialId == null || materialId <= 0) {
            throw new IllegalArgumentException("Output material id must be positive");
        }
        outputMaterialId = materialId;
    }
}
