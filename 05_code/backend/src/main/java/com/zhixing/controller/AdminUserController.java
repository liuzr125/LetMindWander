package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AdminSessionFilter;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AdminUserAccountDetailView;
import com.zhixing.model.AdminUserLearningView;
import com.zhixing.model.AdminUserPageView;
import com.zhixing.model.AdminUserView;
import com.zhixing.service.AdminPrincipal;
import com.zhixing.service.AdminUserService;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
  private final AdminUserService users;
  private final AppProperties properties;

  public AdminUserController(AdminUserService users, AppProperties properties) {
    this.users = users;
    this.properties = properties;
  }

  /** 按昵称、账号标识或手机号筛选账号列表，返回脱敏手机号。 */
  @GetMapping
  public AdminUserPageView users(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "all") String status) {
    admin(token);
    return users.page(page, pageSize, keyword, status);
  }

  /** 管理员查看学习计划、词书进度、生词本与今日任务汇总。 */
  @GetMapping("/{userId}/learning")
  public AdminUserLearningView learning(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String userId,
      @RequestParam(defaultValue = "1") Integer notebookPage,
      @RequestParam(defaultValue = "20") Integer notebookPageSize) {
    admin(token);
    return users.learning(userId, notebookPage, notebookPageSize);
  }

  /** 查看包含完整手机号的账号详情，记录审计并禁止浏览器缓存。 */
  @GetMapping("/{userId}/account")
  public AdminUserAccountDetailView account(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String userId,
      @RequestAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE) AdminPrincipal principal,
      HttpServletResponse response) {
    admin(token);
    response.setHeader("Cache-Control", "no-store");
    return users.accountDetail(userId, principal.getAccountId());
  }

  /** 校验布尔授权值并更新用户 AI 使用授权，同时记录管理员操作审计。 */
  @PutMapping("/{userId}/ai-authorization")
  public AdminUserView aiAuthorization(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String userId,
      @RequestBody Map<String, Object> body,
      @RequestAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE) AdminPrincipal principal) {
    admin(token);
    Object enabled = body == null ? null : body.get("enabled");
    if (!(enabled instanceof Boolean))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_AI_AUTHORIZATION", "enabled 必须为 true 或 false");
    return users.setAiAuthorization(userId, (Boolean) enabled, principal.getAccountId());
  }

  private void admin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
