package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AiPayloadCleanupPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface AiPayloadCleanupPersistenceMapper
    extends BaseMapper<AiPayloadCleanupPersistenceEntity> {
  @Update(
      "UPDATE ai_job SET input_text=NULL,output_json=NULL,updated_at=CURRENT_TIMESTAMP WHERE payload_expires_at<CURRENT_TIMESTAMP AND (input_text IS NOT NULL OR output_json IS NOT NULL)")
  int clearExpiredPayloads();
}
