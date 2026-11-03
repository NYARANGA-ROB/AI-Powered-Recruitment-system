package com.smartats.module.resume.dto;

import lombok.Data;

import java.util.List;

/**
 * Candidate information DTO parsed by AI
 * Corresponds to the JSON structure returned by Zhipu AI
 */
@Data
public class CandidateInfo {

    // Basic information
    private String name;
    private String phone;
    private String email;
    private String gender;
    private Integer age;

    // Education information
    private String education;
    private String school;
    private String major;
    private Integer graduationYear;

    // Work information
    private Integer workYears;
    private String currentCompany;
    private String currentPosition;

    // Skills and experience
    private List<String> skills;
    private List<WorkExperience> workExperience;
    private List<ProjectExperience> projectExperience;
    private String selfEvaluation;

    @Data
    public static class WorkExperience {
        private String company;
        private String position;
        private String startDate;
        private String endDate;
        private String description;
    }

    @Data
    public static class ProjectExperience {
        private String name;
        private String role;
        private String startDate;
        private String endDate;
        private String description;
        private List<String> technologies;
    }
}
