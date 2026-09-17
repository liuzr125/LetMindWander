package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.AiAskRequest;
import com.zhixing.dto.AiModelSaveRequest;
import com.zhixing.model.AiGatewayResult;
import com.zhixing.model.AiRuntimeModel;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AiService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final String GLOBAL_SCOPE="00000000000000000000000000000000";
    private static final Pattern CODE=Pattern.compile("[A-Za-z0-9._:-]{1,120}");
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final AppProperties properties;private final AppParameterService parameters;private final AiGateway gateway;private final TransactionTemplate transactions;
    public AiService(JdbcTemplate jdbc,ObjectMapper json,AppProperties properties,AppParameterService parameters,AiGateway gateway,PlatformTransactionManager transactionManager){this.jdbc=jdbc;this.json=json;this.properties=properties;this.parameters=parameters;this.gateway=gateway;this.transactions=new TransactionTemplate(transactionManager);}

    public Map<String,Object> models(String ownerId){
        String consent=jdbc.queryForObject("SELECT ai_consent_version FROM app_user WHERE id=?",String.class,ownerId);
        LocalDate month=LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
        boolean budget=count("SELECT COUNT(*) FROM ai_month_budget WHERE month_start=?",month)>0;
        List<Map<String,Object>> items=new ArrayList<Map<String,Object>>();
        for(Map<String,Object> row:jdbc.queryForList(modelSelect()+" WHERE m.enabled=1 AND m.is_default=1 ORDER BY m.updated_at DESC LIMIT 1")){
            boolean credential=parameters.configured(string(row,"api_key_param_key"));
            BigDecimal input=decimal(row,"input_per_million"),output=decimal(row,"output_per_million");
            boolean price=input.signum()>0||output.signum()>0;
            Map<String,Object> item=modelView(row);item.remove("baseUrl");item.remove("apiKeyParamKey");item.put("available",credential&&price&&budget);item.put("credentialConfigured",credential);item.put("priceConfigured",price);item.put("budgetConfigured",budget);items.add(item);
        }
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("consentGranted",consent!=null&&!consent.trim().isEmpty());result.put("consentVersion",properties.getAi().getConsentVersion());result.put("models",items);return result;
    }

    public Map<String,Object> ask(String ownerId,AiAskRequest request,String requestKey){
        String question=request.getQuestion()==null?"":request.getQuestion().trim();
        Reservation reservation=transactions.execute(status->reserve(ownerId,null,question,requestKey));
        try{
            AiGatewayResult answer=gateway.ask(reservation.model,reservation.apiKey,question);
            return transactions.execute(status->settleSuccess(reservation,answer));
        }catch(ApiException exception){transactions.executeWithoutResult(status->settleFailure(reservation,exception.getCode()));throw exception;}
        catch(RuntimeException exception){transactions.executeWithoutResult(status->settleFailure(reservation,"AI_PROVIDER_ERROR"));throw exception;}
    }

    public Map<String,Object> history(String ownerId,int size){
        int safe=Math.min(Math.max(size,1),50);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT j.id,j.input_text,j.output_json,j.state,j.error_code,j.created_at,a.input_tokens,a.output_tokens,a.settled_amount,p.currency,p.model_code,m.display_name,m.specification FROM ai_job j LEFT JOIN ai_attempt a ON a.job_id=j.id AND a.attempt_no=1 LEFT JOIN ai_model_price p ON p.id=a.price_id LEFT JOIN ai_model_config m ON m.provider_code=p.provider_code AND m.model_code=p.model_code WHERE j.owner_id=? AND j.action_code='ask_question' ORDER BY j.created_at DESC LIMIT ?",ownerId,safe);
        List<Map<String,Object>> items=new ArrayList<Map<String,Object>>();for(Map<String,Object> row:rows){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("id",value(row,"id"));item.put("question",value(row,"input_text"));item.put("state",value(row,"state"));item.put("errorCode",value(row,"error_code"));item.put("createdAt",value(row,"created_at"));item.put("inputTokens",value(row,"input_tokens"));item.put("outputTokens",value(row,"output_tokens"));item.put("cost",value(row,"settled_amount"));item.put("currency",value(row,"currency"));item.put("modelCode",value(row,"model_code"));item.put("modelName",value(row,"display_name"));item.put("specification",value(row,"specification"));String output=string(row,"output_json");if(output!=null&&!output.isEmpty())try{item.put("answer",json.readTree(output).path("answer").asText());}catch(Exception ignored){}items.add(item);}Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("items",items);return result;
    }

    public List<Map<String,Object>> adminModels(){List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();for(Map<String,Object> row:jdbc.queryForList(modelSelect()+" ORDER BY m.is_default DESC,m.created_at")){Map<String,Object> item=modelView(row);item.put("enabled",number(row,"enabled")==1);item.put("credentialConfigured",parameters.configured(string(row,"api_key_param_key")));result.add(item);}return result;}

    @Transactional
    public Map<String,Object> saveModel(String id,AiModelSaveRequest request){
        validateModel(request);boolean create=id==null||id.trim().isEmpty();String modelId=create?CryptoUtils.randomId():id;
        if(request.isDefaultModel())jdbc.update("UPDATE ai_model_config SET is_default=0,updated_at=CURRENT_TIMESTAMP WHERE is_default=1");
        if(create){
            try{jdbc.update("INSERT INTO ai_model_config(id,provider_code,model_code,display_name,specification,base_url,api_key_param_key,enabled,is_default,max_output_tokens,timeout_seconds,version_no) VALUES(?,?,?,?,?,?,?,?,?,?,?,1)",modelId,request.getProviderCode().trim(),request.getModelCode().trim(),request.getDisplayName().trim(),request.getSpecification().trim().toLowerCase(),request.getBaseUrl().trim(),request.getApiKeyParamKey().trim(),request.isEnabled()?1:0,request.isDefaultModel()?1:0,request.getMaxOutputTokens(),request.getTimeoutSeconds());}catch(DuplicateKeyException e){throw conflict("AI_MODEL_EXISTS","该供应商和模型代码已存在");}
        }else{
            if(request.getExpectedVersion()==null)throw bad("AI_MODEL_VERSION_REQUIRED","缺少模型版本，请刷新后重试");
            int changed=jdbc.update("UPDATE ai_model_config SET provider_code=?,model_code=?,display_name=?,specification=?,base_url=?,api_key_param_key=?,enabled=?,is_default=?,max_output_tokens=?,timeout_seconds=?,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version_no=?",request.getProviderCode().trim(),request.getModelCode().trim(),request.getDisplayName().trim(),request.getSpecification().trim().toLowerCase(),request.getBaseUrl().trim(),request.getApiKeyParamKey().trim(),request.isEnabled()?1:0,request.isDefaultModel()?1:0,request.getMaxOutputTokens(),request.getTimeoutSeconds(),modelId,request.getExpectedVersion());
            if(changed!=1)throw conflict("AI_MODEL_VERSION_CONFLICT","模型配置已变化，请刷新后重试");
        }
        if(request.isEnabled()&&count("SELECT COUNT(*) FROM ai_model_config WHERE enabled=1 AND is_default=1")==0)jdbc.update("UPDATE ai_model_config SET is_default=1 WHERE id=?",modelId);
        if(!request.isEnabled()&&count("SELECT COUNT(*) FROM ai_model_config WHERE enabled=1 AND is_default=1")==0)jdbc.update("UPDATE ai_model_config SET is_default=1 WHERE id=(SELECT chosen.id FROM (SELECT id FROM ai_model_config WHERE enabled=1 ORDER BY updated_at DESC LIMIT 1) chosen)");
        if(request.getApiKey()!=null&&!request.getApiKey().trim().isEmpty())parameters.saveSecret(request.getApiKeyParamKey().trim(),request.getApiKey());
        int priceVersion=number(jdbc.queryForMap("SELECT COALESCE(MAX(version_no),0) AS version_no FROM ai_model_price WHERE provider_code=? AND model_code=?",request.getProviderCode().trim(),request.getModelCode().trim()),"version_no")+1;
        jdbc.update("INSERT INTO ai_model_price(id,provider_code,model_code,version_no,currency,input_per_million,output_per_million,pricing_json,effective_at) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",CryptoUtils.randomId(),request.getProviderCode().trim(),request.getModelCode().trim(),priceVersion,request.getCurrency().trim().toUpperCase(),request.getInputPerMillion(),request.getOutputPerMillion(),"{\"source\":\"admin\",\"verified\":true}");
        audit("config","ai_model_config",modelId,"{\"fields\":[\"model\",\"price\",\"credential_status\"]}");
        for(Map<String,Object> item:adminModels())if(modelId.equals(item.get("id")))return item;throw new IllegalStateException("Saved AI model not found");
    }

    public Map<String,Object> adminUsage(int size){
        int safe=Math.min(Math.max(size,1),200);LocalDate month=LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
        List<Map<String,Object>> logs=jdbc.queryForList("SELECT a.id,a.job_id,a.state,a.provider_request_id,a.input_tokens,a.output_tokens,a.reserved_amount,a.settled_amount,a.error_code,a.started_at,a.finished_at,p.provider_code,p.model_code,p.currency,m.display_name,m.specification FROM ai_attempt a JOIN ai_model_price p ON p.id=a.price_id LEFT JOIN ai_model_config m ON m.provider_code=p.provider_code AND m.model_code=p.model_code ORDER BY a.created_at DESC LIMIT ?",safe);
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("logs",logs);List<Map<String,Object>> budgets=jdbc.queryForList("SELECT id,month_start,currency,limit_amount,reserved_amount,spent_amount,row_version FROM ai_month_budget WHERE month_start=?",month);result.put("budget",budgets.isEmpty()?null:budgets.get(0));return result;
    }

    @Transactional
    public Map<String,Object> saveBudget(BigDecimal limit,String currency){
        if(limit==null||limit.signum()<=0)throw bad("INVALID_AI_BUDGET","月预算必须大于 0");if(currency==null||!currency.matches("[A-Za-z]{3}"))throw bad("INVALID_CURRENCY","货币代码必须是 3 位字母");
        LocalDate month=LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,spent_amount,reserved_amount FROM ai_month_budget WHERE month_start=? FOR UPDATE",month);
        if(rows.isEmpty())jdbc.update("INSERT INTO ai_month_budget(id,month_start,currency,limit_amount,reserved_amount,spent_amount,row_version) VALUES(?,?,?,?,0,0,1)",CryptoUtils.randomId(),month,currency.toUpperCase(),limit);
        else{BigDecimal committed=decimal(rows.get(0),"spent_amount").add(decimal(rows.get(0),"reserved_amount"));if(limit.compareTo(committed)<0)throw conflict("AI_BUDGET_BELOW_COMMITTED","预算不能低于已消费与已预留金额");jdbc.update("UPDATE ai_month_budget SET currency=?,limit_amount=?,row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE month_start=?",currency.toUpperCase(),limit,month);}
        audit("budget","ai_month_budget",null,"{\"month\":\""+month+"\"}");return adminUsage(50);
    }

    @Transactional
    public Map<String,Object> reconcileAttempt(String attemptId,BigDecimal settledAmount){
        if(settledAmount==null||settledAmount.signum()<0)throw bad("INVALID_AI_SETTLEMENT","核定费用不能为负数");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,budget_id,reserved_amount,state FROM ai_attempt WHERE id=? FOR UPDATE",attemptId);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"AI_ATTEMPT_NOT_FOUND","AI 调用记录不存在");Map<String,Object> attempt=rows.get(0);if(!"unknown".equals(string(attempt,"state")))throw conflict("AI_ATTEMPT_NOT_RECONCILABLE","仅未知费用的调用可以人工核对");
        BigDecimal reserved=decimal(attempt,"reserved_amount");jdbc.update("UPDATE ai_month_budget SET reserved_amount=reserved_amount-?,spent_amount=spent_amount+?,row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",reserved,settledAmount,string(attempt,"budget_id"));jdbc.update("UPDATE ai_attempt SET state='failed',settled_amount=?,reconciled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",settledAmount,attemptId);audit("budget","ai_attempt",attemptId,"{\"reconciled\":true}");return adminUsage(100);
    }

    protected Reservation reserve(String ownerId,String requestedModel,String question,String requestKey){
        Map<String,Object> user=jdbc.queryForMap("SELECT ai_consent_version FROM app_user WHERE id=? FOR UPDATE",ownerId);String consent=string(user,"ai_consent_version");if(consent==null||consent.trim().isEmpty())throw new ApiException(HttpStatus.FORBIDDEN,"AI_CONSENT_REQUIRED","使用 AI 前请先阅读并同意 AI 内容发送说明");
        Map<String,Object> modelRow=modelRow(requestedModel);AiRuntimeModel model=runtime(modelRow);String apiKey=parameters.required(model.apiKeyParamKey);if(model.inputPerMillion.signum()==0&&model.outputPerMillion.signum()==0)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_PRICE_NOT_CONFIGURED","模型价格尚未核实，请先在管理端配置");
        LocalDate today=LocalDate.now(BUSINESS_ZONE),month=today.withDayOfMonth(1);ensureQuota(today,ownerId,properties.getAi().getPersonalDailyLimit());ensureQuota(today,GLOBAL_SCOPE,properties.getAi().getGlobalDailyLimit());ensureGuard(ownerId,properties.getAi().getPersonalConcurrency());ensureGuard(GLOBAL_SCOPE,properties.getAi().getGlobalConcurrency());
        reserveQuota(today,ownerId);reserveQuota(today,GLOBAL_SCOPE);reserveGuard(ownerId);reserveGuard(GLOBAL_SCOPE);
        List<Map<String,Object>> budgets=jdbc.queryForList("SELECT * FROM ai_month_budget WHERE month_start=? AND currency=? FOR UPDATE",month,model.currency);if(budgets.isEmpty())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_BUDGET_NOT_CONFIGURED","本月 AI 预算尚未配置");Map<String,Object> budget=budgets.get(0);
        int estimatedInput=Math.max(1,question.codePointCount(0,question.length())+512);BigDecimal reserved=cost(model,estimatedInput,model.maxOutputTokens);int budgetChanged=jdbc.update("UPDATE ai_month_budget SET reserved_amount=reserved_amount+?,row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND spent_amount+reserved_amount+?<=limit_amount",reserved,string(budget,"id"),reserved);if(budgetChanged!=1)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_MONTH_BUDGET_EXCEEDED","本月 AI 预算已用完");
        String jobId=CryptoUtils.randomId(),attemptId=CryptoUtils.randomId(),key=requestKey==null||requestKey.trim().isEmpty()?CryptoUtils.randomToken(18):requestKey.trim();Instant now=Instant.now();
        try{jdbc.update("INSERT INTO ai_job(id,owner_id,scope_key,action_code,source_type,source_id,source_version,source_fingerprint,consent_version,prompt_version,input_text,state,attempt_count,queue_expires_at,payload_expires_at,request_key_hash) VALUES(?,?,?,'ask_question','question',?,1,?,?,?,?,'running',1,?,?,?)",jobId,ownerId,ownerId,jobId,CryptoUtils.sha256(question),consent,"ASK_V1",question,Timestamp.from(now.plusSeconds(600)),Timestamp.from(now.plus(Duration.ofHours(24))),CryptoUtils.sha256(key));}catch(DuplicateKeyException e){throw conflict("AI_REQUEST_DUPLICATE","该问题正在处理或已处理，请查看记录");}
        jdbc.update("INSERT INTO ai_attempt(id,job_id,attempt_no,trigger_type,owner_id,price_id,budget_id,quota_date,state,reserved_amount,timeout_at,quota_reserved,concurrency_held) VALUES(?,?,1,'initial',?,?,?,?, 'reserved',?,?,1,1)",attemptId,jobId,ownerId,model.priceId,string(budget,"id"),today,reserved,Timestamp.from(now.plusSeconds(model.timeoutSeconds)));
        jdbc.update("UPDATE ai_daily_quota SET reserved_count=reserved_count-1,used_count=used_count+1,updated_at=CURRENT_TIMESTAMP WHERE quota_date=? AND scope_key IN (?,?)",today,ownerId,GLOBAL_SCOPE);
        jdbc.update("UPDATE ai_attempt SET state='sending',started_at=CURRENT_TIMESTAMP,quota_reserved=0 WHERE id=?",attemptId);
        Reservation result=new Reservation();result.jobId=jobId;result.attemptId=attemptId;result.ownerId=ownerId;result.budgetId=string(budget,"id");result.reserved=reserved;result.model=model;result.apiKey=apiKey;return result;
    }

    protected Map<String,Object> settleSuccess(Reservation r,AiGatewayResult answer){
        BigDecimal actual=cost(r.model,answer.inputTokens,answer.outputTokens);String output=write(Collections.singletonMap("answer",answer.answer));String usage=write(answer.usage);
        jdbc.update("UPDATE ai_month_budget SET reserved_amount=reserved_amount-?,spent_amount=spent_amount+?,row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",r.reserved,actual,r.budgetId);
        jdbc.update("UPDATE ai_attempt SET state='succeeded',provider_request_id=?,settled_amount=?,input_tokens=?,output_tokens=?,usage_json=?,finished_at=CURRENT_TIMESTAMP,reconciled_at=CURRENT_TIMESTAMP,concurrency_held=0 WHERE id=?",answer.providerRequestId,actual,answer.inputTokens,answer.outputTokens,usage,r.attemptId);
        jdbc.update("UPDATE ai_job SET state='succeeded',output_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",output,r.jobId);releaseGuards(r.ownerId);
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("jobId",r.jobId);result.put("answer",answer.answer);result.put("modelId",r.model.id);result.put("modelName",r.model.displayName);result.put("specification",r.model.specification);result.put("inputTokens",answer.inputTokens);result.put("outputTokens",answer.outputTokens);result.put("cost",actual);result.put("currency",r.model.currency);return result;
    }

    protected void settleFailure(Reservation r,String code){boolean confirmedNoCharge="AI_PROVIDER_REJECTED".equals(code)||"AI_PROVIDER_RATE_LIMIT".equals(code);if(confirmedNoCharge)jdbc.update("UPDATE ai_month_budget SET reserved_amount=reserved_amount-?,row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",r.reserved,r.budgetId);jdbc.update("UPDATE ai_attempt SET state=?,settled_amount=?,finished_at=CURRENT_TIMESTAMP,reconciled_at=?,error_code=?,concurrency_held=0 WHERE id=?",confirmedNoCharge?"failed":"unknown",confirmedNoCharge?BigDecimal.ZERO:null,confirmedNoCharge?Timestamp.from(Instant.now()):null,code,r.attemptId);jdbc.update("UPDATE ai_job SET state='failed',error_code=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",code,r.jobId);releaseGuards(r.ownerId);}

    private String modelSelect(){return "SELECT m.*,p.id price_id,p.currency,p.input_per_million,p.output_per_million,p.version_no price_version FROM ai_model_config m LEFT JOIN ai_model_price p ON p.id=(SELECT p2.id FROM ai_model_price p2 WHERE p2.provider_code=m.provider_code AND p2.model_code=m.model_code ORDER BY p2.effective_at DESC,p2.version_no DESC LIMIT 1)";}
    private Map<String,Object> modelRow(String id){List<Map<String,Object>> rows=id==null||id.trim().isEmpty()?jdbc.queryForList(modelSelect()+" WHERE m.enabled=1 AND m.is_default=1 ORDER BY m.updated_at DESC LIMIT 1"):jdbc.queryForList(modelSelect()+" WHERE m.enabled=1 AND m.id=?",id);if(rows.isEmpty())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_MODEL_UNAVAILABLE","暂无可用 AI 模型");if(value(rows.get(0),"price_id")==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_PRICE_NOT_CONFIGURED","模型价格尚未配置");return rows.get(0);}
    private AiRuntimeModel runtime(Map<String,Object> row){AiRuntimeModel m=new AiRuntimeModel();m.id=string(row,"id");m.providerCode=string(row,"provider_code");m.modelCode=string(row,"model_code");m.displayName=string(row,"display_name");m.specification=string(row,"specification");m.baseUrl=string(row,"base_url");m.apiKeyParamKey=string(row,"api_key_param_key");m.maxOutputTokens=number(row,"max_output_tokens");m.timeoutSeconds=number(row,"timeout_seconds");m.versionNo=number(row,"version_no");m.priceId=string(row,"price_id");m.currency=string(row,"currency");m.inputPerMillion=decimal(row,"input_per_million");m.outputPerMillion=decimal(row,"output_per_million");return m;}
    private Map<String,Object> modelView(Map<String,Object> row){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("id",value(row,"id"));item.put("providerCode",value(row,"provider_code"));item.put("modelCode",value(row,"model_code"));item.put("displayName",value(row,"display_name"));item.put("specification",value(row,"specification"));item.put("baseUrl",value(row,"base_url"));item.put("apiKeyParamKey",value(row,"api_key_param_key"));item.put("defaultModel",number(row,"is_default")==1);item.put("maxOutputTokens",value(row,"max_output_tokens"));item.put("timeoutSeconds",value(row,"timeout_seconds"));item.put("versionNo",value(row,"version_no"));item.put("currency",value(row,"currency"));item.put("inputPerMillion",value(row,"input_per_million"));item.put("outputPerMillion",value(row,"output_per_million"));return item;}
    private void validateModel(AiModelSaveRequest r){if(r==null)throw bad("INVALID_AI_MODEL","模型配置不能为空");if(!CODE.matcher(clean(r.getProviderCode())).matches()||!CODE.matcher(clean(r.getModelCode())).matches())throw bad("INVALID_AI_MODEL_CODE","供应商和模型代码格式不正确");if(!clean(r.getBaseUrl()).startsWith("https://"))throw bad("INVALID_AI_BASE_URL","模型地址必须使用 HTTPS");if(!clean(r.getApiKeyParamKey()).matches("[A-Za-z0-9._-]{3,100}"))throw bad("INVALID_AI_CREDENTIAL_KEY","密钥参数名格式不正确");if(r.getInputPerMillion()==null||r.getOutputPerMillion()==null)throw bad("INVALID_AI_PRICE","请完整配置输入和输出价格");if(r.isDefaultModel()&&!r.isEnabled())throw bad("INVALID_AI_DEFAULT","默认模型必须同时启用");}
    private void ensureQuota(LocalDate date,String scope,int limit){if(count("SELECT COUNT(*) FROM ai_daily_quota WHERE quota_date=? AND scope_key=?",date,scope)==0)try{jdbc.update("INSERT INTO ai_daily_quota(id,quota_date,scope_key,limit_count,used_count,reserved_count) VALUES(?,?,?,?,0,0)",CryptoUtils.randomId(),date,scope,limit);}catch(DuplicateKeyException ignored){}}
    private void reserveQuota(LocalDate date,String scope){if(jdbc.update("UPDATE ai_daily_quota SET reserved_count=reserved_count+1,updated_at=CURRENT_TIMESTAMP WHERE quota_date=? AND scope_key=? AND used_count+reserved_count<limit_count",date,scope)!=1)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_DAILY_QUOTA_EXCEEDED","今日 AI 使用次数已用完");}
    private void ensureGuard(String scope,int limit){if(count("SELECT COUNT(*) FROM ai_concurrency_guard WHERE scope_key=?",scope)==0)try{jdbc.update("INSERT INTO ai_concurrency_guard(id,scope_key,limit_count,running_count) VALUES(?,?,?,0)",CryptoUtils.randomId(),scope,limit);}catch(DuplicateKeyException ignored){}}
    private void reserveGuard(String scope){if(jdbc.update("UPDATE ai_concurrency_guard SET running_count=running_count+1,updated_at=CURRENT_TIMESTAMP WHERE scope_key=? AND running_count<limit_count",scope)!=1)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_CONCURRENCY_EXCEEDED","已有 AI 任务在处理，请稍后再试");}
    private void releaseGuards(String owner){jdbc.update("UPDATE ai_concurrency_guard SET running_count=CASE WHEN running_count>0 THEN running_count-1 ELSE 0 END,updated_at=CURRENT_TIMESTAMP WHERE scope_key IN (?,?)",owner,GLOBAL_SCOPE);}
    private BigDecimal cost(AiRuntimeModel model,int input,int output){return model.inputPerMillion.multiply(BigDecimal.valueOf(Math.max(input,0))).add(model.outputPerMillion.multiply(BigDecimal.valueOf(Math.max(output,0)))).divide(BigDecimal.valueOf(1000000),6,RoundingMode.CEILING);}
    private void audit(String action,String type,String id,String metadata){jdbc.update("INSERT INTO admin_audit(id,admin_id,action_code,target_type,target_id,result_code,metadata_json,expires_at) VALUES(?,?,?,?,?,'success',?,?)",CryptoUtils.randomId(),properties.getAdminPrincipalId(),action,type,id,metadata,Timestamp.from(Instant.now().plus(Duration.ofDays(180))));}
    private int count(String sql,Object...args){Integer value=jdbc.queryForObject(sql,Integer.class,args);return value==null?0:value;}
    private Object value(Map<String,Object> row,String key){Object value=row.get(key);return value==null?row.get(key.toUpperCase()):value;}
    private String string(Map<String,Object> row,String key){Object value=value(row,key);return value==null?null:String.valueOf(value);}
    private int number(Map<String,Object> row,String key){Object value=value(row,key);return value instanceof Number?((Number)value).intValue():value==null?0:Integer.parseInt(String.valueOf(value));}
    private BigDecimal decimal(Map<String,Object> row,String key){Object value=value(row,key);return value==null?BigDecimal.ZERO:value instanceof BigDecimal?(BigDecimal)value:new BigDecimal(String.valueOf(value));}
    private String clean(String value){return value==null?"":value.trim();}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
    private static class Reservation{String jobId,attemptId,ownerId,budgetId,apiKey;BigDecimal reserved;AiRuntimeModel model;}
}
