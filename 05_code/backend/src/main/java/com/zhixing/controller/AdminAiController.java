package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.AiModelSaveRequest;
import com.zhixing.dto.TtsConfigRequest;
import com.zhixing.service.AiService;
import com.zhixing.service.AiOfficialPricingService;
import com.zhixing.service.AliyunTtsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiController {
    private final AiService ai;private final AiOfficialPricingService pricing;private final AliyunTtsService tts;private final AppProperties properties;
    public AdminAiController(AiService ai,AiOfficialPricingService pricing,AliyunTtsService tts,AppProperties properties){this.ai=ai;this.pricing=pricing;this.tts=tts;this.properties=properties;}
    @GetMapping("/models") public List<Map<String,Object>> models(@RequestHeader(value="X-Admin-Token",required=false)String token){requireAdmin(token);return ai.adminModels();}
    @PostMapping("/models") public Map<String,Object> create(@RequestHeader(value="X-Admin-Token",required=false)String token,@Valid @RequestBody AiModelSaveRequest request){requireAdmin(token);return ai.saveModel(null,request);}
    @PutMapping("/models/{id}") public Map<String,Object> update(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String id,@Valid @RequestBody AiModelSaveRequest request){requireAdmin(token);return ai.saveModel(id,request);}
    @GetMapping("/usage") public Map<String,Object> usage(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(defaultValue="100")int size){requireAdmin(token);return ai.adminUsage(size);}
    @GetMapping("/pricing/status") public Map<String,Object> pricingStatus(@RequestHeader(value="X-Admin-Token",required=false)String token){requireAdmin(token);return pricing.status();}
    @PutMapping("/budget") public Map<String,Object> budget(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestBody Map<String,Object> request){requireAdmin(token);Object raw=request.get("limitAmount");BigDecimal limit;try{limit=new BigDecimal(String.valueOf(raw));}catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_AI_BUDGET","请输入正确的月预算");}return ai.saveBudget(limit,String.valueOf(request.getOrDefault("currency","CNY")));}
    @PutMapping("/attempts/{id}/reconcile") public Map<String,Object> reconcile(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String id,@RequestBody Map<String,Object> request){requireAdmin(token);BigDecimal amount;try{amount=new BigDecimal(String.valueOf(request.get("settledAmount")));}catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_AI_SETTLEMENT","请输入正确的核定费用");}return ai.reconcileAttempt(id,amount);}
    @GetMapping("/tts") public Map<String,Object> tts(@RequestHeader(value="X-Admin-Token",required=false)String token){requireAdmin(token);return tts.adminConfig();}
    @PutMapping("/tts") public Map<String,Object> saveTts(@RequestHeader(value="X-Admin-Token",required=false)String token,@Valid @RequestBody TtsConfigRequest request){requireAdmin(token);return tts.saveConfig(request);}
    @PostMapping("/tts/connection-test") public Map<String,Object> testTts(@RequestHeader(value="X-Admin-Token",required=false)String token){requireAdmin(token);return tts.connectionTest();}
    @PostMapping("/tts/account-check") public Map<String,Object> checkTtsAccount(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestBody Map<String,Object> request){requireAdmin(token);return tts.accountCheck(String.valueOf(request.getOrDefault("accountId","")));}
    @PostMapping("/tts/temporary-token-test") public Map<String,Object> testTemporaryToken(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestBody Map<String,Object> request){requireAdmin(token);return tts.previewWithTemporaryToken(String.valueOf(request.getOrDefault("nlsToken","")),String.valueOf(request.getOrDefault("text","")),String.valueOf(request.getOrDefault("voice","")),request.get("sampleRate") instanceof Number?((Number)request.get("sampleRate")).intValue():null);}
    @PostMapping("/tts/temporary-token-production") public Map<String,Object> enableTemporaryTokenProduction(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestBody Map<String,Object> request){requireAdmin(token);return tts.enableTemporaryTokenProduction(String.valueOf(request.getOrDefault("nlsToken","")));}
    @DeleteMapping("/tts/temporary-token-production") public Map<String,Object> disableTemporaryTokenProduction(@RequestHeader(value="X-Admin-Token",required=false)String token){requireAdmin(token);return tts.disableTemporaryTokenProduction();}
    private void requireAdmin(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
