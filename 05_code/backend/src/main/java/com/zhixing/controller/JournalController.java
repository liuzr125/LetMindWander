package com.zhixing.controller;

import com.zhixing.dto.JournalSaveRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.JournalView;
import com.zhixing.service.JournalService;
import com.zhixing.service.SessionService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/journals")
public class JournalController {
  private final SessionService sessions;
  private final JournalService journals;

  public JournalController(SessionService sessions, JournalService journals) {
    this.sessions = sessions;
    this.journals = journals;
  }

  /** 读取当前用户的日记历史。 */
  @GetMapping
  public List<JournalView> history(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return journals.history(user(auth));
  }

  /** 读取指定业务日期的当前用户日记。 */
  @GetMapping("/{date}")
  public JournalView get(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return journals.get(user(auth), date);
  }

  /** 保存指定日期的日记草稿。 */
  @PutMapping("/{date}")
  public JournalView save(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestBody JournalSaveRequest r) {
    return journals.save(user(auth), date, r, false);
  }

  /** 提交指定日期的日记并关联每日任务完成状态。 */
  @PostMapping("/{date}/submit")
  public JournalView submit(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestBody JournalSaveRequest r) {
    return journals.save(user(auth), date, r, true);
  }

  private String user(String auth) {
    AuthenticatedSession s = sessions.requireUser(auth);
    return s.getUserId();
  }
}
