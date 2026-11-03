package com.smartats.module.candidate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartats.common.handler.JsonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Candidate entity
 */
@Data
@TableName("candidates")
public class Candidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Resume ID (1:1 relationship)
     */
    private Long resumeId;

    // ========== Basic information ==========
    private String name;
    private String phone;
    private String email;
    private String gender;

    /** Age extracted by AI (stored in the birth_year column) */
    @TableField("birth_year")
    private Integer age;

    // ========== Education information ==========
    /** Highest education → DB column highest_education */
    @TableField("highest_education")
    private String education;

    /** Graduated school → DB column graduate_school */
    @TableField("graduate_school")
    private String school;

    private String major;

    /** Graduation year → DB column graduation_year (automatic camelCase mapping) */
    private Integer graduationYear;

    // ========== Work information ==========
    /** Years of experience → DB column experience_years */
    @TableField("experience_years")
    private Integer workYears;

    private String currentCompany;
    private String currentPosition;

    // ========== JSON fields ==========
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<String> skills;

    /** Work experience list → DB column work_experiences */
    @TableField(value = "work_experiences", typeHandler = JsonTypeHandler.class)
    private List<Map<String, Object>> workExperience;

    /** Project experience list → DB column project_experience (automatic camelCase mapping) */
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<Map<String, Object>> projectExperience;

    private String selfEvaluation;

    // ========== AI parsing metadata ==========
    /** Raw AI extraction JSON → DB column raw_extracted_json */
    @TableField("raw_extracted_json")
    private String rawJson;

    private Double confidenceScore;
    private LocalDateTime parsedAt;

    // ========== Vector search related ==========
    /** Record ID in the vector database → DB column vector_id */
    private String vectorId;

    /** AI-generated candidate summary (used for embedding and display) → DB column ai_summary */
    private String aiSummary;

    // ========== Audit fields ==========
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}