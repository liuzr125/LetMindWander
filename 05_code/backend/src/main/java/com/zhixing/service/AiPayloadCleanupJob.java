package com.zhixing.service;

import com.zhixing.mapper.AiPayloadCleanupPersistenceMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 临时 AI 正文按需求最多保留 24 小时；计量、状态和费用记录继续保留。 */
@Component
public class AiPayloadCleanupJob {
  private final AiPayloadCleanupPersistenceMapper persistence;

  public AiPayloadCleanupJob(AiPayloadCleanupPersistenceMapper persistence) {
    this.persistence = persistence;
  }

  @Scheduled(fixedDelayString = "${app.ai.cleanup-interval-ms:3600000}")
  public void clean() {
    persistence.clearExpiredPayloads();
  }
}
