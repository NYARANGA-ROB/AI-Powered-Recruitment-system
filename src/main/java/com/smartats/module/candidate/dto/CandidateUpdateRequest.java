package com.smartats.module.candidate.dto;

import lombok.Data;

import java.util.List;

/**
 * Candidate update request DTO
 */
@Data
public class CandidateUpdateRequest {

    private String name;
    private String phone;
    private String email;
    private String gender;
    private Integer age;

    private String education;
    private String school;
    private String major;
    private Integer graduationYear;

    private Integer workYears;
    private String currentCompany;
    private String currentPosition;

    private List<String> skills;
    private String selfEvaluation;
}
