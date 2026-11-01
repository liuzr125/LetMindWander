package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AdminActors;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AdminFeedbackPageView;
import com.zhixing.model.AdminFeedbackView;
import com.zhixing.service.AdminFeedbackService;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 管理端「用户反馈」：查看待处理事项的内容与详情，并可标记处理状态。 */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(AdminFeedbackController.class);
  private final AdminFeedbackService feedback;
  private final AppProperties properties;

  public AdminFeedbackController(AdminFeedbackService feedback, AppProperties properties) {
    this.feedback = feedback;
    this.properties = properties;
  }

  /** 分页读取脱敏反馈摘要及处理状态统计。 */
  @GetMapping
  public AdminFeedbackPageView page(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize,
      @RequestParam(defaultValue = "all") String state,
      @RequestParam(required = false) String keyword) {
    admin(token);
    LOGGER.debug("admin_request controller=Feedback operation=page");
    return feedback.page(page, pageSize, state, keyword);
  }

  /** 读取指定反馈详情，用户联系方式保持脱敏。 */
  @GetMapping("/{id}")
  public AdminFeedbackView detail(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    LOGGER.debug("admin_request controller=Feedback operation=detail");
    return feedback.detail(id);
  }

  /** 更新反馈处理状态并在同一事务内保存管理员审计。 */
  @PutMapping("/{id}/state")
  public AdminFeedbackView state(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id,
      @RequestBody Map<String, Object> body,
      HttpServletRequest servletRequest) {
    admin(token);
    LOGGER.debug("admin_request controller=Feedback operation=state");
    Object state = body == null ? null : body.get("state");
    return feedback.setState(
        id, state == null ? null : String.valueOf(state), AdminActors.requireId(servletRequest));
  }

  private void admin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
