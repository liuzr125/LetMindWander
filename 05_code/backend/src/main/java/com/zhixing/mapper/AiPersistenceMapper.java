package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AiPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface AiPersistenceMapper extends BaseMapper<AiPersistenceEntity> {
  @Select("SELECT ai_consent_version FROM app_user WHERE id=#{id}")
  String selectConsentVersion(@Param("id") Object id);

  @Select("SELECT COUNT(*) FROM ai_month_budget WHERE month_start=#{monthStart}")
  int countMonthBudgets(@Param("monthStart") java.time.LocalDate monthStart);

  @Select(
      "SELECT j.id,j.input_text,j.output_json,j.state,j.error_code,j.created_at,a.input_tokens,a.output_tokens,a.settled_amount,p.currency,p.model_code,m.display_name,m.specification FROM ai_job j LEFT JOIN ai_attempt a ON a.job_id=j.id AND a.attempt_no=1 LEFT JOIN ai_model_price p ON p.id=a.price_id LEFT JOIN ai_model_config m ON m.provider_code=p.provider_code AND m.model_code=p.model_code WHERE j.owner_id=#{ownerId} AND j.action_code='ask_question' AND j.source_type='question' AND j.input_text NOT LIKE '请用简洁中文解释下面这段英语，说明关键词汇、语法结构和自然译文%' ORDER BY j.created_at DESC LIMIT #{limit}")
  List<Map<String, Object>> selectQuestionHistory(
      @Param("ownerId") Object ownerId, @Param("limit") Object limit);

  @Update("UPDATE ai_model_config SET is_default=0,updated_at=CURRENT_TIMESTAMP WHERE is_default=1")
  int clearDefaultModel();

  @Insert(
      "INSERT INTO ai_model_config(id,provider_code,model_code,display_name,specification,base_url,api_key_param_key,enabled,is_default,max_output_tokens,timeout_seconds,version_no) VALUES(#{id},#{providerCode},#{modelCode},#{displayName},#{specification},#{baseUrl},#{apiKeyParamKey},#{enabled},#{isDefault},#{maxOutputTokens},#{timeoutSeconds},1)")
  int insertAiModelConfig(
      @Param("id") Object id,
      @Param("providerCode") Object providerCode,
      @Param("modelCode") Object modelCode,
      @Param("displayName") Object displayName,
      @Param("specification") Object specification,
      @Param("baseUrl") Object baseUrl,
      @Param("apiKeyParamKey") Object apiKeyParamKey,
      @Param("enabled") Object enabled,
      @Param("isDefault") Object isDefault,
      @Param("maxOutputTokens") Object maxOutputTokens,
      @Param("timeoutSeconds") Object timeoutSeconds);

  @Update(
      "UPDATE ai_model_config SET provider_code=#{providerCode},model_code=#{modelCode},display_name=#{displayName},specification=#{specification},base_url=#{baseUrl},api_key_param_key=#{apiKeyParamKey},enabled=#{enabled},is_default=#{isDefault},max_output_tokens=#{maxOutputTokens},timeout_seconds=#{timeoutSeconds},version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND version_no=#{versionNo}")
  int updateModelAtVersion(
      @Param("providerCode") Object providerCode,
      @Param("modelCode") Object modelCode,
      @Param("displayName") Object displayName,
      @Param("specification") Object specification,
      @Param("baseUrl") Object baseUrl,
      @Param("apiKeyParamKey") Object apiKeyParamKey,
      @Param("enabled") Object enabled,
      @Param("isDefault") Object isDefault,
      @Param("maxOutputTokens") Object maxOutputTokens,
      @Param("timeoutSeconds") Object timeoutSeconds,
      @Param("id") Object id,
      @Param("versionNo") Object versionNo);

  @Select("SELECT COUNT(*) FROM ai_model_config WHERE enabled=1 AND is_default=1")
  int countEnabledDefaults();

  @Update("UPDATE ai_model_config SET is_default=1 WHERE id=#{id}")
  int markModelDefault(@Param("id") Object id);

  @Update(
      "UPDATE ai_model_config SET is_default=1 WHERE id=(SELECT chosen.id FROM (SELECT id FROM ai_model_config WHERE enabled=1 ORDER BY updated_at DESC LIMIT 1) chosen)")
  int chooseEnabledDefault();

  @Select(
      "SELECT COALESCE(MAX(version_no),0) AS version_no FROM ai_model_price WHERE provider_code=#{providerCode} AND model_code=#{modelCode}")
  Map<String, Object> selectMaximumPriceVersion(
      @Param("providerCode") Object providerCode, @Param("modelCode") Object modelCode);

  @Insert(
      "INSERT INTO ai_model_price(id,provider_code,model_code,version_no,currency,input_per_million,output_per_million,pricing_json,effective_at) VALUES(#{id},#{providerCode},#{modelCode},#{versionNo},#{currency},#{inputPerMillion},#{outputPerMillion},#{pricingJson},CURRENT_TIMESTAMP)")
  int insertAiModelPrice(
      @Param("id") Object id,
      @Param("providerCode") Object providerCode,
      @Param("modelCode") Object modelCode,
      @Param("versionNo") Object versionNo,
      @Param("currency") Object currency,
      @Param("inputPerMillion") java.math.BigDecimal inputPerMillion,
      @Param("outputPerMillion") java.math.BigDecimal outputPerMillion,
      @Param("pricingJson") Object pricingJson);

  @Select(
      "SELECT a.id,a.job_id,a.state,a.provider_request_id,a.input_tokens,a.output_tokens,a.usage_json,a.reserved_amount,a.settled_amount,a.error_code,a.started_at,a.finished_at,p.provider_code,p.model_code,p.currency,m.display_name,m.specification FROM ai_attempt a JOIN ai_model_price p ON p.id=a.price_id LEFT JOIN ai_model_config m ON m.provider_code=p.provider_code AND m.model_code=p.model_code ORDER BY a.created_at DESC LIMIT #{limit}")
  List<Map<String, Object>> selectUsageLogs(@Param("limit") Object limit);

  @Select(
      "SELECT id,month_start,currency,limit_amount,reserved_amount,spent_amount,row_version FROM ai_month_budget WHERE month_start=#{monthStart}")
  List<Map<String, Object>> selectMonthBudgetSummaries(
      @Param("monthStart") java.time.LocalDate monthStart);

  @Select(
      "SELECT j.id job_id,j.owner_id,j.input_text,j.output_json,j.state job_state,j.error_code,j.created_at,a.state attempt_state,a.provider_request_id,a.input_tokens,a.output_tokens,a.reserved_amount,a.settled_amount,a.started_at,a.finished_at,p.currency,p.model_code,m.display_name,m.specification,u.short_id,u.nickname FROM ai_job j LEFT JOIN ai_attempt a ON a.job_id=j.id AND a.attempt_no=1 LEFT JOIN ai_model_price p ON p.id=a.price_id LEFT JOIN ai_model_config m ON m.provider_code=p.provider_code AND m.model_code=p.model_code LEFT JOIN app_user u ON u.id=j.owner_id WHERE j.id=#{id} AND j.action_code='ask_question' AND j.source_type='question' AND j.input_text NOT LIKE '请用简洁中文解释下面这段英语，说明关键词汇、语法结构和自然译文%'")
  List<Map<String, Object>> selectQuestionAuditDetail(@Param("id") Object id);

  @Select(
      "SELECT id,spent_amount,reserved_amount FROM ai_month_budget WHERE month_start=#{monthStart} FOR UPDATE")
  List<Map<String, Object>> lockMonthBudgetAmounts(
      @Param("monthStart") java.time.LocalDate monthStart);

  @Insert(
      "INSERT INTO ai_month_budget(id,month_start,currency,limit_amount,reserved_amount,spent_amount,row_version) VALUES(#{id},#{monthStart},#{currency},#{limitAmount},0,0,1)")
  int insertAiMonthBudget(
      @Param("id") Object id,
      @Param("monthStart") java.time.LocalDate monthStart,
      @Param("currency") Object currency,
      @Param("limitAmount") java.math.BigDecimal limitAmount);

  @Update(
      "UPDATE ai_month_budget SET currency=#{currency},limit_amount=#{limitAmount},row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE month_start=#{monthStart}")
  int updateMonthBudgetLimit(
      @Param("currency") Object currency,
      @Param("limitAmount") java.math.BigDecimal limitAmount,
      @Param("monthStart") java.time.LocalDate monthStart);

  @Select("SELECT id,budget_id,reserved_amount,state FROM ai_attempt WHERE id=#{id} FOR UPDATE")
  List<Map<String, Object>> lockAttemptForReconciliation(@Param("id") Object id);

  @Update(
      "UPDATE ai_month_budget SET reserved_amount=reserved_amount-#{reservedAmount},spent_amount=spent_amount+#{spentAmount},row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int settleBudgetReservation(
      @Param("reservedAmount") java.math.BigDecimal reservedAmount,
      @Param("spentAmount") java.math.BigDecimal spentAmount,
      @Param("id") Object id);

  @Update(
      "UPDATE ai_attempt SET state='failed',settled_amount=#{settledAmount},reconciled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int reconcileAttempt(
      @Param("settledAmount") java.math.BigDecimal settledAmount, @Param("id") Object id);

  @Select("SELECT id,ai_consent_version FROM app_user WHERE id=#{id} FOR UPDATE")
  Map<String, Object> lockUserConsent(@Param("id") Object id);

  @Select(
      "SELECT * FROM ai_month_budget WHERE month_start=#{monthStart} AND currency=#{currency} FOR UPDATE")
  List<Map<String, Object>> lockBudgetInCurrency(
      @Param("monthStart") java.time.LocalDate monthStart, @Param("currency") Object currency);

  @Update(
      "UPDATE ai_month_budget SET reserved_amount=reserved_amount+#{reservedAmount},row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND spent_amount+reserved_amount+#{reservedAmountValue3}<=limit_amount")
  int reserveBudgetIfAvailable(
      @Param("reservedAmount") java.math.BigDecimal reservedAmount,
      @Param("id") Object id,
      @Param("reservedAmountValue3") Object reservedAmountValue3);

  @Insert(
      "INSERT INTO ai_job(id,owner_id,scope_key,action_code,source_type,source_id,source_version,source_fingerprint,consent_version,prompt_version,input_text,state,attempt_count,queue_expires_at,payload_expires_at,request_key_hash) VALUES(#{id},#{ownerId},#{scopeKey},'ask_question','question',#{sourceId},1,#{sourceFingerprint.bytes,jdbcType=BINARY},#{consentVersion},#{promptVersion},#{inputText},'running',1,#{queueExpiresAt},#{payloadExpiresAt},#{requestKeyHash.bytes,jdbcType=BINARY})")
  int insertQuestionJob(
      @Param("id") Object id,
      @Param("ownerId") Object ownerId,
      @Param("scopeKey") Object scopeKey,
      @Param("sourceId") Object sourceId,
      @Param("sourceFingerprint") com.zhixing.mapper.type.BinaryHash sourceFingerprint,
      @Param("consentVersion") Object consentVersion,
      @Param("promptVersion") Object promptVersion,
      @Param("inputText") Object inputText,
      @Param("queueExpiresAt") java.sql.Timestamp queueExpiresAt,
      @Param("payloadExpiresAt") java.sql.Timestamp payloadExpiresAt,
      @Param("requestKeyHash") com.zhixing.mapper.type.BinaryHash requestKeyHash);

  @Insert(
      "INSERT INTO ai_attempt(id,job_id,attempt_no,trigger_type,owner_id,price_id,budget_id,quota_date,state,reserved_amount,timeout_at,quota_reserved,concurrency_held) VALUES(#{id},#{jobId},1,'initial',#{ownerId},#{priceId},#{budgetId},#{quotaDate}, 'reserved',#{reservedAmount},#{timeoutAt},1,1)")
  int insertInitialAttempt(
      @Param("id") Object id,
      @Param("jobId") Object jobId,
      @Param("ownerId") Object ownerId,
      @Param("priceId") Object priceId,
      @Param("budgetId") Object budgetId,
      @Param("quotaDate") java.time.LocalDate quotaDate,
      @Param("reservedAmount") java.math.BigDecimal reservedAmount,
      @Param("timeoutAt") java.sql.Timestamp timeoutAt);

  @Update(
      "UPDATE ai_daily_quota SET reserved_count=reserved_count-1,used_count=used_count+1,updated_at=CURRENT_TIMESTAMP WHERE quota_date=#{quotaDate} AND scope_key IN (#{updatedAtValue2},#{gLOBAL_SCOPE})")
  int consumeReservedDailyQuota(
      @Param("quotaDate") java.time.LocalDate quotaDate,
      @Param("updatedAtValue2") Object updatedAtValue2,
      @Param("gLOBAL_SCOPE") Object gLOBAL_SCOPE);

  @Update(
      "UPDATE ai_attempt SET state='sending',started_at=CURRENT_TIMESTAMP,quota_reserved=0 WHERE id=#{id}")
  int markAttemptSending(@Param("id") Object id);

  @Insert(
      "INSERT INTO ai_job(id,owner_id,scope_key,action_code,source_type,source_id,source_version,source_fingerprint,consent_version,prompt_version,input_text,state,attempt_count,queue_expires_at,payload_expires_at,request_key_hash) VALUES(#{id},#{ownerId},#{scopeKey},#{actionCode},#{sourceType},#{sourceId},1,#{sourceFingerprint.bytes,jdbcType=BINARY},'SYSTEM_CONTENT_V1',#{promptVersion},#{inputText},'running',1,#{queueExpiresAt},#{payloadExpiresAt},#{requestKeyHash.bytes,jdbcType=BINARY})")
  int insertSystemJob(
      @Param("id") Object id,
      @Param("ownerId") Object ownerId,
      @Param("scopeKey") Object scopeKey,
      @Param("actionCode") Object actionCode,
      @Param("sourceType") Object sourceType,
      @Param("sourceId") Object sourceId,
      @Param("sourceFingerprint") com.zhixing.mapper.type.BinaryHash sourceFingerprint,
      @Param("promptVersion") Object promptVersion,
      @Param("inputText") Object inputText,
      @Param("queueExpiresAt") java.sql.Timestamp queueExpiresAt,
      @Param("payloadExpiresAt") java.sql.Timestamp payloadExpiresAt,
      @Param("requestKeyHash") com.zhixing.mapper.type.BinaryHash requestKeyHash);

  @Insert(
      "INSERT INTO ai_attempt(id,job_id,attempt_no,trigger_type,owner_id,price_id,budget_id,quota_date,state,reserved_amount,timeout_at,quota_reserved,concurrency_held) VALUES(#{id},#{jobId},1,'scheduled',#{ownerId},#{priceId},#{budgetId},#{quotaDate}, 'reserved',#{reservedAmount},#{timeoutAt},1,1)")
  int insertScheduledAttempt(
      @Param("id") Object id,
      @Param("jobId") Object jobId,
      @Param("ownerId") Object ownerId,
      @Param("priceId") Object priceId,
      @Param("budgetId") Object budgetId,
      @Param("quotaDate") java.time.LocalDate quotaDate,
      @Param("reservedAmount") java.math.BigDecimal reservedAmount,
      @Param("timeoutAt") java.sql.Timestamp timeoutAt);

  @Update(
      "UPDATE ai_attempt SET state='succeeded',provider_request_id=#{providerRequestId},settled_amount=#{settledAmount},input_tokens=#{inputTokens},output_tokens=#{outputTokens},usage_json=#{usageJson},finished_at=CURRENT_TIMESTAMP,reconciled_at=CURRENT_TIMESTAMP,concurrency_held=0 WHERE id=#{id}")
  int markAttemptSucceeded(
      @Param("providerRequestId") Object providerRequestId,
      @Param("settledAmount") java.math.BigDecimal settledAmount,
      @Param("inputTokens") Object inputTokens,
      @Param("outputTokens") Object outputTokens,
      @Param("usageJson") Object usageJson,
      @Param("id") Object id);

  @Update(
      "UPDATE ai_job SET state='succeeded',output_json=#{outputJson},updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int markJobSucceeded(@Param("outputJson") Object outputJson, @Param("id") Object id);

  @Update(
      "UPDATE ai_month_budget SET reserved_amount=reserved_amount-#{reservedAmount},row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int releaseBudgetReservation(
      @Param("reservedAmount") java.math.BigDecimal reservedAmount, @Param("id") Object id);

  @Update(
      "UPDATE ai_attempt SET state=#{state},settled_amount=#{settledAmount},finished_at=CURRENT_TIMESTAMP,reconciled_at=#{reconciledAt},error_code=#{errorCode},concurrency_held=0 WHERE id=#{id}")
  int markAttemptFailedOrUnknown(
      @Param("state") Object state,
      @Param("settledAmount") java.math.BigDecimal settledAmount,
      @Param("reconciledAt") java.sql.Timestamp reconciledAt,
      @Param("errorCode") Object errorCode,
      @Param("id") Object id);

  @Update(
      "UPDATE ai_job SET state='failed',error_code=#{errorCode},updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int markJobFailed(@Param("errorCode") Object errorCode, @Param("id") Object id);

  @Select(
      "SELECT COUNT(*) FROM ai_daily_quota WHERE quota_date=#{quotaDate} AND scope_key=#{scopeKey}")
  int countDailyQuota(
      @Param("quotaDate") java.time.LocalDate quotaDate, @Param("scopeKey") Object scopeKey);

  @Insert(
      "INSERT INTO ai_daily_quota(id,quota_date,scope_key,limit_count,used_count,reserved_count) VALUES(#{id},#{quotaDate},#{scopeKey},#{limitCount},0,0)")
  int insertAiDailyQuota(
      @Param("id") Object id,
      @Param("quotaDate") java.time.LocalDate quotaDate,
      @Param("scopeKey") Object scopeKey,
      @Param("limitCount") Object limitCount);

  @Update(
      "UPDATE ai_daily_quota SET limit_count=#{limitCount},updated_at=CURRENT_TIMESTAMP WHERE quota_date=#{quotaDate} AND scope_key=#{scopeKey} AND limit_count<>#{limitCountValue4}")
  int updateDailyQuotaLimitIfChanged(
      @Param("limitCount") Object limitCount,
      @Param("quotaDate") java.time.LocalDate quotaDate,
      @Param("scopeKey") Object scopeKey,
      @Param("limitCountValue4") Object limitCountValue4);

  @Update(
      "UPDATE ai_daily_quota SET reserved_count=reserved_count+1,updated_at=CURRENT_TIMESTAMP WHERE quota_date=#{quotaDate} AND scope_key=#{scopeKey} AND used_count+reserved_count<limit_count")
  int reserveDailyQuotaIfAvailable(
      @Param("quotaDate") java.time.LocalDate quotaDate, @Param("scopeKey") Object scopeKey);

  @Select("SELECT COUNT(*) FROM ai_concurrency_guard WHERE scope_key=#{scopeKey}")
  int countConcurrencyGuard(@Param("scopeKey") Object scopeKey);

  @Insert(
      "INSERT INTO ai_concurrency_guard(id,scope_key,limit_count,running_count) VALUES(#{id},#{scopeKey},#{limitCount},0)")
  int insertAiConcurrencyGuard(
      @Param("id") Object id,
      @Param("scopeKey") Object scopeKey,
      @Param("limitCount") Object limitCount);

  @Update(
      "UPDATE ai_concurrency_guard SET running_count=running_count+1,updated_at=CURRENT_TIMESTAMP WHERE scope_key=#{scopeKey} AND running_count<limit_count")
  int acquireConcurrencyIfAvailable(@Param("scopeKey") Object scopeKey);

  @Update(
      "UPDATE ai_concurrency_guard SET running_count=CASE WHEN running_count>0 THEN running_count-1 ELSE 0 END,updated_at=CURRENT_TIMESTAMP WHERE scope_key IN (#{scopeKey},#{globalScope})")
  int releaseConcurrency(
      @Param("scopeKey") Object scopeKey, @Param("globalScope") Object globalScope);

  @Insert(
      "INSERT INTO admin_audit(id,admin_id,action_code,target_type,target_id,result_code,metadata_json,expires_at) VALUES(#{id},#{adminId},#{actionCode},#{targetType},#{targetId},'success',#{metadataJson},#{expiresAt})")
  int insertAdminAudit(
      @Param("id") Object id,
      @Param("adminId") Object adminId,
      @Param("actionCode") Object actionCode,
      @Param("targetType") Object targetType,
      @Param("targetId") Object targetId,
      @Param("metadataJson") Object metadataJson,
      @Param("expiresAt") java.sql.Timestamp expiresAt);

  @Select(
      "SELECT m.*,p.id price_id,p.currency,p.input_per_million,p.output_per_million,p.pricing_json,p.effective_at price_effective_at,p.version_no price_version FROM ai_model_config m LEFT JOIN ai_model_price p ON p.id=(SELECT p2.id FROM ai_model_price p2 WHERE p2.provider_code=m.provider_code AND p2.model_code=m.model_code ORDER BY p2.effective_at DESC,p2.version_no DESC LIMIT 1) WHERE m.enabled=1 AND m.is_default=1 ORDER BY m.updated_at DESC LIMIT 1")
  List<Map<String, Object>> selectDefaultModel();

  @Select(
      "SELECT m.*,p.id price_id,p.currency,p.input_per_million,p.output_per_million,p.pricing_json,p.effective_at price_effective_at,p.version_no price_version FROM ai_model_config m LEFT JOIN ai_model_price p ON p.id=(SELECT p2.id FROM ai_model_price p2 WHERE p2.provider_code=m.provider_code AND p2.model_code=m.model_code ORDER BY p2.effective_at DESC,p2.version_no DESC LIMIT 1) ORDER BY m.is_default DESC,m.created_at")
  List<Map<String, Object>> selectAdminModels();

  @Select(
      "SELECT m.*,p.id price_id,p.currency,p.input_per_million,p.output_per_million,p.pricing_json,p.effective_at price_effective_at,p.version_no price_version FROM ai_model_config m LEFT JOIN ai_model_price p ON p.id=(SELECT p2.id FROM ai_model_price p2 WHERE p2.provider_code=m.provider_code AND p2.model_code=m.model_code ORDER BY p2.effective_at DESC,p2.version_no DESC LIMIT 1) WHERE m.enabled=1 AND m.id=#{id}")
  List<Map<String, Object>> selectEnabledModel(@Param("id") Object id);

  @Select(
      "SELECT COUNT(*) FROM ai_job j LEFT JOIN ai_attempt a ON a.job_id=j.id AND a.attempt_no=1 LEFT JOIN ai_model_price p ON p.id=a.price_id LEFT JOIN ai_model_config m ON m.provider_code=p.provider_code AND m.model_code=p.model_code LEFT JOIN app_user u ON u.id=j.owner_id WHERE j.action_code='ask_question' AND j.source_type='question' AND j.input_text NOT LIKE '请用简洁中文解释下面这段英语，说明关键词汇、语法结构和自然译文%' AND (#{jobId}='' OR LOCATE(#{jobId},j.input_text)>0 OR LOCATE(#{jobId},COALESCE(u.nickname,''))>0 OR LOCATE(#{jobId},COALESCE(u.short_id,''))>0)")
  Integer countQuestionAudit(@Param("jobId") Object jobId);

  @Select(
      "SELECT j.id job_id,j.owner_id,j.state job_state,j.error_code,j.created_at,a.state attempt_state,p.model_code,m.display_name,m.specification,u.short_id,u.nickname FROM ai_job j LEFT JOIN ai_attempt a ON a.job_id=j.id AND a.attempt_no=1 LEFT JOIN ai_model_price p ON p.id=a.price_id LEFT JOIN ai_model_config m ON m.provider_code=p.provider_code AND m.model_code=p.model_code LEFT JOIN app_user u ON u.id=j.owner_id WHERE j.action_code='ask_question' AND j.source_type='question' AND j.input_text NOT LIKE '请用简洁中文解释下面这段英语，说明关键词汇、语法结构和自然译文%' AND (#{jobId}='' OR LOCATE(#{jobId},j.input_text)>0 OR LOCATE(#{jobId},COALESCE(u.nickname,''))>0 OR LOCATE(#{jobId},COALESCE(u.short_id,''))>0) ORDER BY j.created_at DESC LIMIT #{limit} OFFSET #{offset}")
  List<Map<String, Object>> selectQuestionAuditPage(
      @Param("jobId") Object jobId, @Param("limit") Object limit, @Param("offset") Object offset);
}
