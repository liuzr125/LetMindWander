package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AdminOverviewService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overview")
public class AdminOverviewController {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(AdminOverviewController.class);
  private final AdminOverviewService overview;
  private final AppProperties properties;

  public AdminOverviewController(AdminOverviewService overview, AppProperties properties) {
    this.overview = overview;
    this.properties = properties;
  }

  /** 按北京时间业务日汇总用户、内容、任务、反馈及本月 AI 支出。 */
  @GetMapping
  public Map<String, Object> overview(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    LOGGER.debug("admin_request controller=Overview operation=overview");
    return overview.overview();
  }

  private void admin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
