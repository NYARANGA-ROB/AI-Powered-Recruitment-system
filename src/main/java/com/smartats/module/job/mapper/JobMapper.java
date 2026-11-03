package com.smartats.module.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.job.entity.Job;
import org.apache.ibatis.annotations.Mapper;

/**
 * Job Mapper
 */
@Mapper
public interface JobMapper extends BaseMapper<Job> {
    // MyBatis-Plus automatically provides CRUD methods, no XML needed
}