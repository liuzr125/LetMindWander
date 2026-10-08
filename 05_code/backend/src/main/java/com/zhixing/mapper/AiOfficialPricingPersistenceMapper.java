package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AiOfficialPricingPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface AiOfficialPricingPersistenceMapper
    extends BaseMapper<AiOfficialPricingPersistenceEntity> {
  @Select(
      "SELECT id,trigger_key,state,changed_model_count,error_code,error_message,started_at,finished_at,created_at FROM ai_pricing_sync_run ORDER BY created_at DESC,id DESC LIMIT #{limit}")
  List<Map<String, Object>> selectRecentSyncRuns(@Param("limit") Object limit);

  @Insert(
      "INSERT INTO ai_pricing_sync_run(id,trigger_key,state,started_at) VALUES(#{id},#{triggerKey}, 'running', CURRENT_TIMESTAMP)")
  int insertSyncRun(@Param("id") Object id, @Param("triggerKey") Object triggerKey);

  @Update(
      "UPDATE ai_pricing_sync_run SET state='success',changed_model_count=#{changedModelCount},finished_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int markSyncSucceeded(
      @Param("changedModelCount") Object changedModelCount, @Param("id") Object id);

  @Update(
      "UPDATE ai_pricing_sync_run SET state='failed',error_code=#{errorCode},error_message=#{errorMessage},finished_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int markSyncFailed(
      @Param("errorCode") Object errorCode,
      @Param("errorMessage") Object errorMessage,
      @Param("id") Object id);

  @Select(
      "SELECT id,trigger_key,state,changed_model_count,error_code,error_message,started_at,finished_at,created_at FROM ai_pricing_sync_run WHERE trigger_key LIKE #{triggerKey} ORDER BY created_at DESC,id DESC LIMIT #{limit}")
  List<Map<String, Object>> selectSyncRunsByTrigger(
      @Param("triggerKey") Object triggerKey, @Param("limit") Object limit);

  @Select(
      "SELECT pricing_json FROM ai_model_price WHERE provider_code='deepseek' AND model_code=#{modelCode} ORDER BY effective_at DESC,version_no DESC LIMIT 1")
  List<Map<String, Object>> selectLatestOfficialPrice(@Param("modelCode") Object modelCode);

  @Select(
      "SELECT COALESCE(MAX(version_no),0) FROM ai_model_price WHERE provider_code='deepseek' AND model_code=#{modelCode}")
  Integer selectMaximumOfficialPriceVersion(@Param("modelCode") Object modelCode);

  @Insert(
      "INSERT INTO ai_model_price(id,provider_code,model_code,version_no,currency,input_per_million,output_per_million,pricing_json,effective_at) VALUES(#{id},'deepseek',#{modelCode},#{versionNo},'CNY',#{inputPerMillion},#{outputPerMillion},#{pricingJson},CURRENT_TIMESTAMP)")
  int insertOfficialPrice(
      @Param("id") Object id,
      @Param("modelCode") Object modelCode,
      @Param("versionNo") Object versionNo,
      @Param("inputPerMillion") java.math.BigDecimal inputPerMillion,
      @Param("outputPerMillion") java.math.BigDecimal outputPerMillion,
      @Param("pricingJson") Object pricingJson);
}
