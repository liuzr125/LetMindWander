package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

/** Auditable user AI conversation and cost ledger. */
@RestController
@RequestMapping("/api/admin/ai-audit")
public class AdminAiAuditController {
    private final AiService ai; private final AppProperties properties;
    public AdminAiAuditController(AiService ai,AppProperties properties){this.ai=ai;this.properties=properties;}
    @GetMapping("/questions") public Map<String,Object> questions(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String keyword){require(token);return ai.adminQuestionAudit(page,pageSize,keyword);}
    @GetMapping("/questions/{jobId}/detail") public Map<String,Object> questionDetail(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String jobId){require(token);return ai.adminQuestionAuditDetail(jobId);}
    private void require(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
