package com.zhixing.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 临时 AI 正文按需求最多保留 24 小时；计量、状态和费用记录继续保留。 */
@Component
public class AiPayloadCleanupJob {
    private final JdbcTemplate jdbc;
    public AiPayloadCleanupJob(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Scheduled(fixedDelayString="${app.ai.cleanup-interval-ms:3600000}")
    public void clean(){jdbc.update("UPDATE ai_job SET input_text=NULL,output_json=NULL,updated_at=CURRENT_TIMESTAMP WHERE payload_expires_at<CURRENT_TIMESTAMP AND (input_text IS NOT NULL OR output_json IS NOT NULL)");}
}
