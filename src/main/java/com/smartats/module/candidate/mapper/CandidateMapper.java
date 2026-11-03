package com.smartats.module.candidate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.candidate.entity.Candidate;
import org.apache.ibatis.annotations.Mapper;

/**
 * Candidate Mapper
 */
@Mapper
public interface CandidateMapper extends BaseMapper<Candidate> {
}
