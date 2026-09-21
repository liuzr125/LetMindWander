package com.zhixing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AiGatewayResult;
import com.zhixing.model.AiRuntimeModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiGatewayTest {
    private final AiGateway gateway=new AiGateway(new ObjectMapper(),new AppProperties());

    @Test
    void structuredDeepSeekRequestDisablesThinkingAndAsksForJson(){
        AiRuntimeModel model=new AiRuntimeModel();model.providerCode="deepseek";model.modelCode="deepseek-flash";model.maxOutputTokens=1200;
        Map<String,Object> request=gateway.requestBody(model,"Return JSON","Generate passages",true);
        assertEquals("disabled",((Map<?,?>)request.get("thinking")).get("type"));
        assertEquals("json_object",((Map<?,?>)request.get("response_format")).get("type"));
        assertEquals(1200,request.get("max_tokens"));
        assertFalse(gateway.requestBody(model,"system","user",false).containsKey("thinking"));
    }

    @Test
    void parsesNormalChatCompletionAndUsage()throws Exception{
        AiGatewayResult result=gateway.parseResponse("{\"id\":\"req-1\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"articles\\\":[]}\"}}],\"usage\":{\"prompt_tokens\":200,\"completion_tokens\":80,\"prompt_cache_hit_tokens\":20}}");
        assertEquals("{\"articles\":[]}",result.answer);
        assertEquals("req-1",result.providerRequestId);
        assertEquals(200,result.inputTokens);
        assertEquals(80,result.outputTokens);
        assertEquals(20,result.cacheHitInputTokens);
        assertEquals(180,result.cacheMissInputTokens);
    }

    @Test
    void distinguishesTruncationFromEmptyAnswer() {
        ApiException truncated=assertThrows(ApiException.class,()->gateway.parseResponse("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}"));
        assertEquals("AI_RESPONSE_TRUNCATED",truncated.getCode());
        ApiException empty=assertThrows(ApiException.class,()->gateway.parseResponse("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null}}]}"));
        assertEquals("AI_RESPONSE_EMPTY",empty.getCode());
        assertEquals("AI_RESPONSE_EMPTY",assertThrows(ApiException.class,()->gateway.parseResponse("  ")).getCode());
    }

    @Test
    void rejectsNonChatEnvelopeWithoutEchoingItsContents(){
        ApiException exception=assertThrows(ApiException.class,()->gateway.parseResponse("{\"status\":\"ok\",\"private_text\":\"secret\"}"));
        assertEquals("AI_RESPONSE_INVALID",exception.getCode());
        assertFalse(exception.getMessage().contains("secret"));
    }
}
