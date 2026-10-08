package com.zhixing.controller;

import com.zhixing.dto.ConfirmWeeklyActionsRequest;
import com.zhixing.dto.WeeklyActionSaveRequest;
import com.zhixing.dto.WeeklySummaryConfirmRequest;
import com.zhixing.dto.WeeklySummaryEditRequest;
import com.zhixing.model.ActionPlanView;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.GrowthOverviewView;
import com.zhixing.model.WeeklySummaryView;
import com.zhixing.service.GrowthService;
import com.zhixing.service.SessionService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GrowthController {
  private final SessionService sessions;
  private final GrowthService growth;

  public GrowthController(SessionService sessions, GrowthService growth) {
    this.sessions = sessions;
    this.growth = growth;
  }

  /** 读取当前用户指定周的成长统计。 */
  @GetMapping("/growth")
  public GrowthOverviewView overview(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart) {
    return growth.overview(user(auth), weekStart);
  }

  /** 读取指定周的当前用户周总结。 */
  @GetMapping("/weekly-summaries/{weekStart}")
  public WeeklySummaryView summary(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
    return growth.summary(user(auth), weekStart, true);
  }

  /** 保存当前用户编辑的周总结。 */
  @PutMapping("/weekly-summaries/{weekStart}")
  public WeeklySummaryView edit(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @RequestBody WeeklySummaryEditRequest request) {
    return growth.edit(user(auth), weekStart, request);
  }

  /** 确认指定周总结。 */
  @PostMapping("/weekly-summaries/{weekStart}/confirm")
  public WeeklySummaryView confirm(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @RequestBody WeeklySummaryConfirmRequest request) {
    return growth.confirm(user(auth), weekStart, request);
  }

  /** 按当前学习事实重新计算指定周总结。 */
  @PostMapping("/weekly-summaries/{weekStart}/recalculate")
  public WeeklySummaryView recalculate(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
    return growth.recalculate(user(auth), weekStart);
  }

  /** 读取指定周的行动计划。 */
  @GetMapping("/weekly-summaries/{weekStart}/actions")
  public ActionPlanView actions(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
    return growth.actionPlan(user(auth), weekStart);
  }

  /** 保存指定周的行动计划条目。 */
  @PostMapping("/weekly-summaries/{weekStart}/actions")
  public ActionPlanView saveAction(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @RequestBody WeeklyActionSaveRequest request) {
    return growth.saveAction(user(auth), weekStart, request);
  }

  /** 取消当前用户指定周的行动条目。 */
  @DeleteMapping("/weekly-summaries/{weekStart}/actions/{actionId}")
  public ActionPlanView cancelAction(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @PathVariable String actionId,
      @RequestParam Integer expectedVersion) {
    return growth.cancelAction(user(auth), weekStart, actionId, expectedVersion);
  }

  /** 确认指定周的行动计划。 */
  @PostMapping("/weekly-summaries/{weekStart}/actions/confirm")
  public ActionPlanView confirmActions(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      @RequestBody(required = false) ConfirmWeeklyActionsRequest request) {
    return growth.confirmActions(user(auth), weekStart, request);
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
