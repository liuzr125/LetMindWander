package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.ManagedTaskScheduler;
import com.zhixing.service.TaskScheduleSettings;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tasks")
public class AdminTaskScheduleController {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(AdminTaskScheduleController.class);
  private final ManagedTaskScheduler scheduler;
  private final TaskScheduleSettings settings;
  private final AppProperties properties;

  public AdminTaskScheduleController(
      ManagedTaskScheduler scheduler, TaskScheduleSettings settings, AppProperties properties) {
    this.scheduler = scheduler;
    this.settings = settings;
    this.properties = properties;
  }

  /** 验证管理员与 Cron 文本，更新执行计划并返回下次运行时间。 */
  @PutMapping("/{code}/schedule")
  public Map<String, Object> save(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String code,
      @RequestBody Map<String, Object> body) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
    Object cron = body == null ? null : body.get("cronExpression");
    if (!(cron instanceof String))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_CRON", "执行计划必须是文本");
    Map<String, Object> result = new LinkedHashMap<>();
    String saved = scheduler.update(code, (String) cron);
    result.put("cronExpression", saved);
    result.put("scheduleApplied", true);
    // Update the live scheduler before reporting the new plan; invalid expressions must not appear
    // saved.
    LOGGER.info("Admin task schedule updated");
    result.put("timezone", TaskScheduleSettings.ZONE.getId());
    result.put("nextRunAt", settings.nextFromCron(saved, Instant.now()));
    return result;
  }
}
