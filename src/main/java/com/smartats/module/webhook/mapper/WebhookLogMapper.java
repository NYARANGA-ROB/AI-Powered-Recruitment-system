package com.smartats.module.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.webhook.entity.WebhookLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Webhook log Mapper
 */
@Mapper
public interface WebhookLogMapper extends BaseMapper<WebhookLog> {
}
