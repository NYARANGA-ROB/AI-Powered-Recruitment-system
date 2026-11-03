package com.smartats.module.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.application.entity.JobApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * Job application Mapper
 */
@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplication> {
}