package com.zhixing.controller;

import com.zhixing.dto.TaskEventRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.DailyPackageView;
import com.zhixing.model.DailyTaskView;
import com.zhixing.service.DailyTaskService;
import com.zhixing.service.SessionService;
import java.time.LocalDate;
import javax.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/** F04 今日任务：用户只能读取和变更自己的任务，客户端从不提交完成率。 */
@RestController
public class DailyTaskController {
  private final SessionService sessions;
  private final DailyTaskService daily;

  public DailyTaskController(SessionService sessions, DailyTaskService daily) {
    this.sessions = sessions;
    this.daily = daily;
  }

  /** 读取指定业务日期的每日任务及完成统计。 */
  @GetMapping("/api/days/{date}")
  public DailyPackageView day(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return daily.get(user(auth), date);
  }

  /** 激活指定日期的任务包，保留已开始和已完成任务事实。 */
  @PostMapping("/api/days/{date}/activate")
  public DailyPackageView activate(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return daily.activate(user(auth), date, true);
  }

  /** 今日英语新词首次进入随机选词，之后返回相同的数据库任务范围。 */
  @PostMapping("/api/days/{date}/words/select")
  public DailyPackageView selectWords(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return daily.selectWords(user(auth), date);
  }

  /** 通过版本校验推进任务状态，禁止绕过单词学习完成校验。 */
  @PostMapping("/api/tasks/{id}/events")
  public DailyTaskView event(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable("id") String id,
      @Valid @RequestBody TaskEventRequest request) {
    return daily.event(user(auth), id, request);
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
