package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AdminActors;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AiService;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Auditable user AI conversation and cost ledger. */
@RestController
@RequestMapping("/api/admin/ai-audit")
public class AdminAiAuditController {
  private final AiService ai;
  private final AppProperties properties;

  public AdminAiAuditController(AiService ai, AppProperties properties) {
    this.ai = ai;
    this.properties = properties;
  }

  /** 分页查询用户提问审计摘要，完整会话需显式读取详情。 */
  @GetMapping("/questions")
  public Map<String, Object> questions(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      HttpServletResponse response) {
    noStore(response);
    require(token);
    return ai.adminQuestionAudit(page, pageSize, keyword);
  }

  /** 读取指定提问的完整审计及费用详情，仅管理员可访问。 */
  @GetMapping("/questions/{jobId}/detail")
  public Map<String, Object> questionDetail(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String jobId,
      HttpServletRequest request,
      HttpServletResponse response) {
    noStore(response);
    require(token);
    return ai.adminQuestionAuditDetail(jobId, AdminActors.requireId(request));
  }

  private void noStore(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Pragma", "no-cache");
  }

  private void require(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
