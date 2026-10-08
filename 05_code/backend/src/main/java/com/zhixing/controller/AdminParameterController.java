package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AppParameterService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 系统运行参数仅向已验证的管理端开放；敏感参数永不通过列表接口返回原文。 */
@RestController
@RequestMapping("/api/admin/parameters")
public class AdminParameterController {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(AdminParameterController.class);
  private final AppParameterService parameters;
  private final AppProperties properties;

  public AdminParameterController(AppParameterService parameters, AppProperties properties) {
    this.parameters = parameters;
    this.properties = properties;
  }

  /** 分页读取系统参数，敏感参数值由服务层隐藏。 */
  @GetMapping
  public Map<String, Object> list(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "10") Integer pageSize) {
    admin(token);
    LOGGER.debug("admin_request controller=Parameter operation=list");
    return parameters.adminPage(keyword, page, pageSize);
  }

  /** 校验并创建系统参数，敏感值加密保存。 */
  @PostMapping
  public Map<String, Object> create(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    admin(token);
    LOGGER.debug("admin_request controller=Parameter operation=create");
    return parameters.adminCreate(body == null ? Collections.<String, Object>emptyMap() : body);
  }

  /** 更新指定系统参数，空请求交由服务层进行字段校验。 */
  @PutMapping("/{id}")
  public Map<String, Object> update(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id,
      @RequestBody Map<String, Object> body) {
    admin(token);
    LOGGER.debug("admin_request controller=Parameter operation=update");
    return parameters.adminUpdate(id, body == null ? Collections.<String, Object>emptyMap() : body);
  }

  /** 软删除参数，使运行中的参数查询不再使用该项。 */
  @DeleteMapping("/{id}")
  public Map<String, Object> delete(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    LOGGER.debug("admin_request controller=Parameter operation=delete");
    parameters.adminSoftDelete(id);
    return Collections.<String, Object>singletonMap("deleted", true);
  }

  private void admin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
