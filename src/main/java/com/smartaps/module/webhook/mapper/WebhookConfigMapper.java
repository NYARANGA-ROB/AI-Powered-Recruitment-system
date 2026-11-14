package com.smartaps.module.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartaps.module.webhook.entity.WebhookConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Webhook configuration Mapper
 */
@Mapper
public interface WebhookConfigMapper extends BaseMapper<WebhookConfig> {
}
