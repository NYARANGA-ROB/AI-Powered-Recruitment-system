package com.smartaps.module.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartaps.module.application.entity.JobApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * Job application Mapper
 */
@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplication> {
}