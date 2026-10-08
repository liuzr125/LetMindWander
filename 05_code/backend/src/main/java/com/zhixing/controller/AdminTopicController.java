package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AdminSessionFilter;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AdminPrincipal;
import com.zhixing.service.AdminTopicService;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/topics")
public class AdminTopicController {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(AdminTopicController.class);
  private final AdminTopicService topics;
  private final AppProperties properties;

  public AdminTopicController(AdminTopicService topics, AppProperties properties) {
    this.topics = topics;
    this.properties = properties;
  }

  /** 读取未软删除的系统主题及内容、计划引用统计。 */
  @GetMapping
  public List<Map<String, Object>> list(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    require(token);
    LOGGER.debug("admin_request controller=Topic operation=list");
    return topics.list();
  }

  /** 校验名称与分类，创建系统主题并记录审计。 */
  @PostMapping
  public Map<String, Object> create(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, String> body,
      HttpServletRequest request) {
    require(token);
    LOGGER.debug("admin_request controller=Topic operation=create");
    topics.save(actor(request), null, body.get("name"), body.get("topicType"));
    return Collections.singletonMap("message", "主题已创建");
  }

  /** 锁定系统主题后更新名称或分类，保留引用保护。 */
  @PutMapping("/{id}")
  public Map<String, Object> update(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id,
      @RequestBody Map<String, String> body,
      HttpServletRequest request) {
    require(token);
    LOGGER.debug("admin_request controller=Topic operation=update");
    topics.save(actor(request), id, body.get("name"), body.get("topicType"));
    return Collections.singletonMap("message", "主题已保存");
  }

  /** 仅软删除无发布技术内容和学习计划引用的系统主题。 */
  @DeleteMapping("/{id}")
  public Map<String, Object> delete(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id,
      HttpServletRequest request) {
    require(token);
    LOGGER.debug("admin_request controller=Topic operation=delete");
    topics.delete(actor(request), id);
    return Collections.singletonMap("message", "主题已删除");
  }

  private String actor(HttpServletRequest request) {
    // Session principals retain audit attribution; the legacy token uses its configured principal.
    Object value = request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE);
    return value instanceof AdminPrincipal
        ? ((AdminPrincipal) value).getAccountId()
        : properties.getAdminPrincipalId();
  }

  private void require(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_REQUIRED", "需要管理员登录");
  }
}
