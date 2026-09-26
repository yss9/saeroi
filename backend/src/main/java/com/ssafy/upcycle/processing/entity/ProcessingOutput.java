package com.ssafy.upcycle.processing.entity;

import com.ssafy.upcycle.material.entity.MaterialType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Entity @Table(name = "processing_output")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingOutput {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "processing_job_id", nullable = false) private Long processingJobId;
    @Column(name = "processing_input_id", nullable = false) private Long processingInputId;
    @Column(name = "output_material_id") private Long outputMaterialId;
    @Enumerated(EnumType.STRING) @Column(name = "material_type", nullable = false, length = 50) private MaterialType materialType;
    @Column(nullable = false, length = 50) private String grade;
    @Column(name = "grade_reason", nullable = false, length = 500) private String gradeReason;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false) private long quantity;

    public static ProcessingOutput draft(Long jobId, Long inputId, MaterialType type,
            String grade, String gradeReason, String name, long quantity) {
        if (jobId == null || inputId == null || type == null || grade == null || grade.isBlank()
                || !grade.trim().matches("[ABC]") || gradeReason == null || gradeReason.isBlank()
                || gradeReason.trim().length() > 500 || name == null || name.isBlank()
                || name.trim().length() > 200 || quantity <= 0)
            throw new IllegalArgumentException("Valid output fields are required");
        ProcessingOutput output = new ProcessingOutput();
        output.processingJobId=jobId; output.processingInputId=inputId; output.materialType=type;
        output.grade=grade.trim(); output.gradeReason=gradeReason.trim(); output.name=name.trim();
        output.quantity=quantity;
        return output;
    }

    public static ProcessingOutput submit(Long jobId, Long inputId, MaterialType type,
            String name, String grade, String gradeReason, long quantity) {
        return draft(jobId, inputId, type, grade, gradeReason, name, quantity);
    }

    public void linkMaterial(Long materialId) {
        if (materialId == null || materialId <= 0 || outputMaterialId != null)
            throw new IllegalStateException("Output material cannot be linked");
        outputMaterialId = materialId;
    }
}
