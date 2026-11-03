package com.smartats.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.audit.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Audit log Mapper interface
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
