package com.zhixing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AiGatewayResult;
import com.zhixing.model.AiRuntimeModel;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Service
public class AiGateway {
    private static final Logger LOGGER=LoggerFactory.getLogger(AiGateway.class);
    private final ObjectMapper json;private final AppProperties properties;
    public AiGateway(ObjectMapper json,AppProperties properties){this.json=json;this.properties=properties;}

    public AiGatewayResult ask(AiRuntimeModel model,String apiKey,String question){
        return ask(model,apiKey,"你是脑袋开小灶的学习助手。请用准确、简洁的中文回答；不确定时明确说明，不编造事实。",question);
    }

    public AiGatewayResult ask(AiRuntimeModel model,String apiKey,String systemPrompt,String question){
        return ask(model,apiKey,systemPrompt,question,false);
    }

    /** Structured generation needs the answer tokens for JSON, not the provider's default reasoning budget. */
    public AiGatewayResult askStructured(AiRuntimeModel model,String apiKey,String systemPrompt,String question){
        return ask(model,apiKey,systemPrompt,question,true);
    }

    private AiGatewayResult ask(AiRuntimeModel model,String apiKey,String systemPrompt,String question,boolean structured){
        if(properties.getAi().isMockEnabled()){
            AiGatewayResult result=new AiGatewayResult();result.answer="这是测试环境的 AI 回答："+question;result.providerRequestId="mock-"+UUID.randomUUID();result.inputTokens=Math.max(1,(systemPrompt.length()+question.length())/2);result.outputTokens=Math.max(1,result.answer.length()/2);result.cacheMissInputTokens=result.inputTokens;result.usage=new LinkedHashMap<String,Object>();result.usage.put("prompt_tokens",result.inputTokens);result.usage.put("completion_tokens",result.outputTokens);result.usage.put("prompt_cache_hit_tokens",0);result.usage.put("prompt_cache_miss_tokens",result.inputTokens);return result;
        }
        if(model.baseUrl==null||!model.baseUrl.startsWith("https://"))throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_ENDPOINT_INVALID","AI 模型地址必须使用 HTTPS");
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth(apiKey);
        Map<String,Object> body=requestBody(model,systemPrompt,question,structured);
        try{
            SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Math.min(10000,model.timeoutSeconds*1000));factory.setReadTimeout(model.timeoutSeconds*1000);RestTemplate http=new RestTemplate(factory);
            ResponseEntity<String> response=http.exchange(model.baseUrl,HttpMethod.POST,new HttpEntity<Map<String,Object>>(body,headers),String.class);
            return parseResponse(response.getBody());
        }catch(HttpStatusCodeException exception){String code=exception.getStatusCode().value()==429?"AI_PROVIDER_RATE_LIMIT":"AI_PROVIDER_REJECTED";throw new ApiException(HttpStatus.BAD_GATEWAY,code,exception.getStatusCode().value()==429?"AI 服务繁忙，请稍后再试":"AI 供应商拒绝了请求，请检查模型与密钥配置");}
        catch(RestClientException exception){throw new ApiException(HttpStatus.BAD_GATEWAY,"AI_PROVIDER_ERROR","AI 服务暂时不可用，请稍后再试");}
        catch(ApiException exception){throw exception;}
        catch(Exception exception){LOGGER.warn("AI response parsing failed: model={}, cause={}",model.modelCode,exception.getClass().getSimpleName());throw new ApiException(HttpStatus.BAD_GATEWAY,"AI_RESPONSE_INVALID","AI 服务返回非 JSON 内容，请检查模型接口地址与响应格式");}
    }

    Map<String,Object> requestBody(AiRuntimeModel model,String systemPrompt,String question,boolean structured){
        Map<String,Object> body=new LinkedHashMap<String,Object>();body.put("model",model.modelCode);body.put("stream",false);body.put("max_tokens",model.maxOutputTokens);
        if(structured&&"deepseek".equalsIgnoreCase(model.providerCode)){
            body.put("thinking",Collections.singletonMap("type","disabled"));
            body.put("response_format",Collections.singletonMap("type","json_object"));
        }
        List<Map<String,String>> messages=new ArrayList<Map<String,String>>();Map<String,String> system=new LinkedHashMap<String,String>();system.put("role","system");system.put("content",systemPrompt);Map<String,String> user=new LinkedHashMap<String,String>();user.put("role","user");user.put("content",question);messages.add(system);messages.add(user);body.put("messages",messages);return body;
    }

    AiGatewayResult parseResponse(String payload)throws Exception{
        if(payload==null||payload.trim().isEmpty())throw new ApiException(HttpStatus.BAD_GATEWAY,"AI_RESPONSE_EMPTY","AI 服务返回空响应，请稍后重试");
        JsonNode root=json.readTree(payload);
        if(root==null||!root.isObject()||!root.path("choices").isArray()||root.path("choices").size()==0){
            LOGGER.warn("AI response lacks chat choices; top-level fields={}",root!=null&&root.isObject()?fieldNames(root):"non-object");
            throw new ApiException(HttpStatus.BAD_GATEWAY,"AI_RESPONSE_INVALID","AI 服务未返回聊天补全结果，请检查模型接口地址");
        }
        JsonNode choice=root.path("choices").get(0);
        if("length".equals(choice.path("finish_reason").asText()))throw new ApiException(HttpStatus.BAD_GATEWAY,"AI_RESPONSE_TRUNCATED","AI 输出达到 token 上限；请提高模型最大输出 token 数");
        JsonNode content=choice.path("message").path("content");
        if(!content.isTextual()||content.asText().trim().isEmpty())throw new ApiException(HttpStatus.BAD_GATEWAY,"AI_RESPONSE_EMPTY","AI 服务未生成正文，请检查输出 token 上限后重试");
        JsonNode usageNode=root.path("usage");
        Map<String,Object> usage=usageNode.isObject()?json.convertValue(usageNode,Map.class):new LinkedHashMap<String,Object>();
        AiGatewayResult result=new AiGatewayResult();result.answer=content.asText().trim();result.providerRequestId=root.path("id").isTextual()?root.path("id").asText():null;result.inputTokens=number(usage.get("prompt_tokens"));result.outputTokens=number(usage.get("completion_tokens"));result.cacheHitInputTokens=number(usage.get("prompt_cache_hit_tokens"));result.cacheMissInputTokens=number(usage.get("prompt_cache_miss_tokens"));if(result.cacheHitInputTokens+result.cacheMissInputTokens<result.inputTokens)result.cacheMissInputTokens=result.inputTokens-result.cacheHitInputTokens;result.usage=usage;return result;
    }
    private List<String> fieldNames(JsonNode node){List<String> fields=new ArrayList<String>();node.fieldNames().forEachRemaining(fields::add);return fields;}
    private int number(Object value){return value instanceof Number?((Number)value).intValue():0;}
}
